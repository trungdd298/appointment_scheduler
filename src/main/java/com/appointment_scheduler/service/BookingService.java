package com.appointment_scheduler.service;

import com.appointment_scheduler.domain.Appointment;
import com.appointment_scheduler.domain.AppointmentStatus;
import com.appointment_scheduler.domain.Dealership;
import com.appointment_scheduler.domain.Technician;
import com.appointment_scheduler.domain.VehicleRef;
import com.appointment_scheduler.repository.AppointmentRepository;
import com.appointment_scheduler.repository.CustomerRefRepository;
import com.appointment_scheduler.repository.DealershipRepository;
import com.appointment_scheduler.repository.ServiceBayRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.repository.TechnicianRepository;
import com.appointment_scheduler.repository.VehicleRefRepository;
import com.appointment_scheduler.service.dto.HoldCommand;
import com.appointment_scheduler.service.exception.HoldExpiredException;
import com.appointment_scheduler.service.exception.HoldTokenMismatchException;
import com.appointment_scheduler.service.exception.ResourceNotFoundException;
import com.appointment_scheduler.service.exception.SlotNoLongerAvailableException;
import com.appointment_scheduler.service.exception.TechnicianNotQualifiedException;
import com.appointment_scheduler.service.exception.ValidationException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hold → Confirm booking. See .claude/docs/booking-flow.md and
 * .claude/docs/architecture.md#concurrency-correctness-defense-in-depth —
 * the Postgres EXCLUDE constraints are the actual guarantee; this class is
 * the "best-effort, purely for UX" application layer on top: it searches
 * for a candidate bay and retries the INSERT against the constraint rather
 * than pre-locking anything.
 */
@Service
public class BookingService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    /**
     * Bounded retry across candidate bays — not specified in the design
     * docs beyond "bounded number of times" (design-decisions §1). Capped
     * well above any realistic per-dealership bay count so it only limits
     * pathological cases, not normal operation.
     */
    private static final int MAX_BAY_RETRY_ATTEMPTS = 10;

    private final DealershipRepository dealershipRepository;
    private final VehicleRefRepository vehicleRefRepository;
    private final CustomerRefRepository customerRefRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final TechnicianRepository technicianRepository;
    private final ServiceBayRepository serviceBayRepository;
    private final AppointmentRepository appointmentRepository;
    private final DurationResolver durationResolver;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public BookingService(
            DealershipRepository dealershipRepository,
            VehicleRefRepository vehicleRefRepository,
            CustomerRefRepository customerRefRepository,
            ServiceTypeRepository serviceTypeRepository,
            TechnicianRepository technicianRepository,
            ServiceBayRepository serviceBayRepository,
            AppointmentRepository appointmentRepository,
            DurationResolver durationResolver,
            PlatformTransactionManager transactionManager) {
        this.dealershipRepository = dealershipRepository;
        this.vehicleRefRepository = vehicleRefRepository;
        this.customerRefRepository = customerRefRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.technicianRepository = technicianRepository;
        this.serviceBayRepository = serviceBayRepository;
        this.appointmentRepository = appointmentRepository;
        this.durationResolver = durationResolver;
        // Each candidate-bay insert attempt runs in its own transaction,
        // deliberately NOT via a sibling @Transactional method (which would
        // silently no-op on self-invocation through the AOP proxy).
        // Postgres also aborts a transaction outright after any SQL error
        // (constraint violations included) — reusing one transaction across
        // attempts would make every attempt after the first fail regardless
        // of whether that candidate was actually free.
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(
                TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    // Deliberately NOT @Transactional: this method's own reads don't need a
    // consistent snapshot (the EXCLUDE constraint, not read consistency, is
    // what actually prevents double-booking — see the class Javadoc), and
    // wrapping it in one would hold a pooled connection for the entire
    // method while attemptInsertHold's REQUIRES_NEW attempts each check out
    // a second one, needlessly doubling connection usage under concurrent
    // load.
    public Appointment hold(HoldCommand command) {
        Dealership dealership =
                dealershipRepository
                        .findById(command.dealershipId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Dealership " + command.dealershipId() + " not found"));
        VehicleRef vehicle =
                vehicleRefRepository
                        .findById(command.vehicleId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Vehicle " + command.vehicleId() + " not found"));
        customerRefRepository
                .findById(command.customerId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Customer " + command.customerId() + " not found"));
        serviceTypeRepository
                .findById(command.serviceTypeId())
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Service type " + command.serviceTypeId() + " not found"));
        Technician technician =
                technicianRepository
                        .findById(command.technicianId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Technician " + command.technicianId() + " not found"));

        // findQualifiedActive already scopes by dealership, so this single
        // membership check also validates the technician belongs to the
        // requested dealership — no separate lazy technician.getDealership()
        // access needed (which would risk LazyInitializationException once
        // outside this transaction anyway).
        boolean qualified =
                technicianRepository.findQualifiedActive(command.dealershipId(), command.serviceTypeId()).stream()
                        .anyMatch(t -> t.getId().equals(technician.getId()));
        if (!qualified) {
            throw new TechnicianNotQualifiedException(
                    "Technician "
                            + technician.getId()
                            + " is not qualified for service type "
                            + command.serviceTypeId());
        }

        int durationMinutes =
                durationResolver.resolveDurationMinutes(command.serviceTypeId(), vehicle.getVehicleCategory());
        OffsetDateTime start = command.startTime();
        OffsetDateTime end = start.plusMinutes(durationMinutes);

        if (!BusinessHours.fits(dealership, start, end)) {
            throw new ValidationException("Requested time is outside dealership business hours");
        }

        if (!appointmentRepository.isTechnicianFree(technician.getId(), start, end)) {
            throw new SlotNoLongerAvailableException(
                    "Technician " + technician.getId() + " is no longer available for the requested time");
        }

        List<Long> candidateBayIds =
                appointmentRepository.findFreeBayIds(command.dealershipId(), start, end);
        int attempts = Math.min(candidateBayIds.size(), MAX_BAY_RETRY_ATTEMPTS);
        for (int i = 0; i < attempts; i++) {
            Long appointmentId =
                    attemptInsertHold(command, dealership.getId(), candidateBayIds.get(i), technician.getId(), start, end);
            if (appointmentId != null) {
                return appointmentRepository.findByIdWithAssociations(appointmentId).orElseThrow();
            }
        }
        throw new SlotNoLongerAvailableException(
                "No bay available for the requested time after " + attempts + " attempt(s)");
    }

    /** Returns the new appointment's id on success, or null if this candidate bay lost the race. */
    private Long attemptInsertHold(
            HoldCommand command,
            Long dealershipId,
            Long bayId,
            Long technicianId,
            OffsetDateTime start,
            OffsetDateTime end) {
        try {
            return requiresNewTransactionTemplate.execute(
                    status -> {
                        Appointment appointment =
                                Appointment.builder()
                                        .dealership(dealershipRepository.getReferenceById(dealershipId))
                                        .bay(serviceBayRepository.getReferenceById(bayId))
                                        .technician(technicianRepository.getReferenceById(technicianId))
                                        .vehicle(vehicleRefRepository.getReferenceById(command.vehicleId()))
                                        .customer(customerRefRepository.getReferenceById(command.customerId()))
                                        .serviceType(serviceTypeRepository.getReferenceById(command.serviceTypeId()))
                                        .startTime(start)
                                        .endTime(end)
                                        .status(AppointmentStatus.HELD)
                                        .expiresAt(OffsetDateTime.now().plus(HOLD_TTL))
                                        .build();
                        Appointment saved = appointmentRepository.saveAndFlush(appointment);
                        return saved.getId();
                    });
        } catch (DataAccessException e) {
            // Another request claimed this bay between our read and this
            // INSERT — the EXCLUDE constraint is what actually caught it
            // (DataIntegrityViolationException). Under heavy concurrency,
            // Postgres can also report a genuine deadlock while checking
            // the constraint (CannotAcquireLockException) rather than a
            // clean violation — that's still just "this attempt didn't
            // work," not a different outcome, so it's handled the same
            // way: move on to the next candidate. Catching the broad
            // DataAccessException deliberately, rather than enumerating
            // every specific failure mode this narrow single-row INSERT
            // could hit.
            return null;
        }
    }

    // noRollbackFor: the HELD -> EXPIRED write right before throwing
    // HoldExpiredException below must survive even though the method is
    // signaling failure — otherwise Spring's default rollback-on-unchecked-
    // exception behavior silently discards that status write.
    @Transactional(noRollbackFor = HoldExpiredException.class)
    public Appointment confirm(Long appointmentId, UUID holdToken) {
        Appointment appointment =
                appointmentRepository
                        .findByIdWithAssociations(appointmentId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Appointment " + appointmentId + " not found"));

        if (!appointment.getHoldToken().equals(holdToken)) {
            throw new HoldTokenMismatchException(
                    "Hold token does not match for appointment " + appointmentId);
        }

        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            return appointment; // idempotent: same token, already confirmed
        }

        if (appointment.getStatus() != AppointmentStatus.HELD) {
            throw new HoldExpiredException(
                    "Appointment "
                            + appointmentId
                            + " is no longer confirmable (status="
                            + appointment.getStatus()
                            + ")");
        }

        if (appointment.getExpiresAt() != null && appointment.getExpiresAt().isBefore(OffsetDateTime.now())) {
            appointment.setStatus(AppointmentStatus.EXPIRED);
            appointmentRepository.saveAndFlush(appointment);
            throw new HoldExpiredException(
                    "Hold for appointment " + appointmentId + " expired at " + appointment.getExpiresAt());
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setExpiresAt(null);
        return appointmentRepository.saveAndFlush(appointment);
    }

    /**
     * Only reachable from HELD or CONFIRMED, matching the documented state
     * diagram (.claude/docs/booking-flow.md) — CANCELLED is reachable from
     * NEEDS_ATTENTION only via the escalation sweep, not this direct path.
     */
    @Transactional
    public Appointment cancel(Long appointmentId) {
        Appointment appointment =
                appointmentRepository
                        .findByIdWithAssociations(appointmentId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Appointment " + appointmentId + " not found"));

        if (appointment.getStatus() != AppointmentStatus.HELD
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ValidationException(
                    "Appointment "
                            + appointmentId
                            + " cannot be cancelled from status "
                            + appointment.getStatus());
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setExpiresAt(null);
        return appointmentRepository.saveAndFlush(appointment);
    }
}
