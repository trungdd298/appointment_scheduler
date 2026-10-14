package com.appointment_scheduler.service;

import com.appointment_scheduler.domain.Appointment;
import com.appointment_scheduler.domain.AppointmentStatus;
import com.appointment_scheduler.domain.ResourceType;
import com.appointment_scheduler.domain.ResourceUnavailability;
import com.appointment_scheduler.domain.Technician;
import com.appointment_scheduler.repository.AppointmentRepository;
import com.appointment_scheduler.repository.ResourceUnavailabilityRepository;
import com.appointment_scheduler.repository.ServiceBayRepository;
import com.appointment_scheduler.repository.TechnicianRepository;
import com.appointment_scheduler.service.dto.RecordUnavailabilityCommand;
import com.appointment_scheduler.service.exception.ResourceNotFoundException;
import com.appointment_scheduler.service.exception.SlotNoLongerAvailableException;
import com.appointment_scheduler.service.exception.TechnicianNotQualifiedException;
import com.appointment_scheduler.service.exception.ValidationException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records resource unavailability and reconciles any CONFIRMED appointments
 * it affects. See .claude/docs/reconciliation.md for the full model — the
 * two resource types are handled deliberately differently:
 *
 * <ul>
 *   <li>Bay: auto-reassigned to a free replacement bay, silently — the bay
 *       was never a customer choice.
 *   <li>Technician: moved to NEEDS_ATTENTION, never auto-reassigned — the
 *       technician was an explicit customer choice. {@link
 *       #reassignTechnician} is the manual ops resolution path; there is no
 *       automatic replacement-technician-finding logic (out of scope — see
 *       .claude/docs/assumptions.md).
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ResourceUnavailabilityService {

    private final ResourceUnavailabilityRepository resourceUnavailabilityRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceBayRepository serviceBayRepository;
    private final TechnicianRepository technicianRepository;

    @Transactional
    public ResourceUnavailability recordUnavailability(RecordUnavailabilityCommand command) {
        var existing = resourceUnavailabilityRepository.findByExternalEventId(command.externalEventId());
        if (existing.isPresent()) {
            return existing.get(); // idempotent replay of an already-processed external event
        }

        ResourceUnavailability unavailability =
                ResourceUnavailability.builder()
                        .resourceType(command.resourceType())
                        .resourceId(command.resourceId())
                        .startTime(command.startTime())
                        .endTime(command.endTime())
                        .reason(command.reason())
                        .source(command.source())
                        .externalEventId(command.externalEventId())
                        .build();
        ResourceUnavailability saved = resourceUnavailabilityRepository.saveAndFlush(unavailability);

        if (command.resourceType() == ResourceType.BAY) {
            reconcileBayUnavailability(command.resourceId(), command.startTime(), command.endTime());
        } else {
            reconcileTechnicianUnavailability(command.resourceId(), command.startTime(), command.endTime());
        }

        return saved;
    }

    private void reconcileBayUnavailability(Long bayId, OffsetDateTime start, OffsetDateTime end) {
        List<Appointment> affected = appointmentRepository.findConfirmedByBayOverlapping(bayId, start, end);
        for (Appointment appointment : affected) {
            // The just-saved unavailability row (flushed above) already
            // excludes this bay from the free-bay search within this same
            // transaction.
            List<Long> replacementBayIds =
                    appointmentRepository.findFreeBayIds(
                            appointment.getDealership().getId(),
                            appointment.getStartTime(),
                            appointment.getEndTime());
            if (!replacementBayIds.isEmpty()) {
                appointment.setBay(serviceBayRepository.getReferenceById(replacementBayIds.get(0)));
                appointmentRepository.saveAndFlush(appointment);
            } else {
                // Not explicitly covered by the design docs: leaving the
                // appointment CONFIRMED against a bay that's now unavailable
                // would be actively wrong, so this also escalates to ops
                // rather than silently doing nothing. See assumptions.md.
                markNeedsAttention(
                        appointment, "No replacement bay available after bay " + bayId + " became unavailable");
            }
        }
    }

    private void reconcileTechnicianUnavailability(Long technicianId, OffsetDateTime start, OffsetDateTime end) {
        List<Appointment> affected =
                appointmentRepository.findConfirmedByTechnicianOverlapping(technicianId, start, end);
        for (Appointment appointment : affected) {
            markNeedsAttention(appointment, "Technician " + technicianId + " became unavailable");
        }
    }

    private void markNeedsAttention(Appointment appointment, String reason) {
        appointment.setStatus(AppointmentStatus.NEEDS_ATTENTION);
        appointment.setNeedsAttentionReason(reason);
        // "Near the appointment time" per the design docs — not pinned to
        // an exact offset in the docs, so the deadline is the appointment's
        // own start time: unresolved by then, auto-cancel rather than leave
        // the customer showing up to no one available. See assumptions.md.
        appointment.setEscalationDeadline(appointment.getStartTime());
        appointmentRepository.saveAndFlush(appointment);
    }

    /**
     * Ops manually resolves a NEEDS_ATTENTION appointment by choosing the
     * replacement technician directly — there is no automatic suggestion.
     */
    @Transactional
    public Appointment reassignTechnician(Long appointmentId, Long newTechnicianId) {
        Appointment appointment =
                appointmentRepository
                        .findByIdWithAssociations(appointmentId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Appointment " + appointmentId + " not found"));

        if (appointment.getStatus() != AppointmentStatus.NEEDS_ATTENTION) {
            throw new ValidationException(
                    "Appointment "
                            + appointmentId
                            + " is not awaiting reassignment (status="
                            + appointment.getStatus()
                            + ")");
        }

        Technician newTechnician =
                technicianRepository
                        .findById(newTechnicianId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Technician " + newTechnicianId + " not found"));

        boolean qualified =
                technicianRepository
                        .findQualifiedActive(appointment.getDealership().getId(), appointment.getServiceType().getId())
                        .stream()
                        .anyMatch(t -> t.getId().equals(newTechnicianId));
        if (!qualified) {
            throw new TechnicianNotQualifiedException(
                    "Technician "
                            + newTechnicianId
                            + " is not qualified for service type "
                            + appointment.getServiceType().getId());
        }

        if (!appointmentRepository.isTechnicianFree(
                newTechnicianId, appointment.getStartTime(), appointment.getEndTime())) {
            throw new SlotNoLongerAvailableException(
                    "Technician " + newTechnicianId + " is not available at that time");
        }

        appointment.setTechnician(newTechnician);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setNeedsAttentionReason(null);
        appointment.setEscalationDeadline(null);
        return appointmentRepository.saveAndFlush(appointment);
    }
}
