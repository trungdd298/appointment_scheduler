package com.appointment_scheduler.service;

import com.appointment_scheduler.domain.Dealership;
import com.appointment_scheduler.domain.Technician;
import com.appointment_scheduler.domain.VehicleRef;
import com.appointment_scheduler.repository.AppointmentRepository;
import com.appointment_scheduler.repository.DealershipRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.repository.TechnicianRepository;
import com.appointment_scheduler.repository.VehicleRefRepository;
import com.appointment_scheduler.service.dto.AvailabilitySlot;
import com.appointment_scheduler.service.exception.ResourceNotFoundException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * GET /dealerships/{id}/availability — browse-then-commit search. Unlocked,
 * best-effort: results can go stale the instant they're returned. See
 * .claude/docs/booking-flow.md#browse-then-commit — the hold step never
 * trusts this output and re-runs its own candidate search.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    /**
     * Candidate slots are stepped at 15-minute increments. Not specified in
     * the design docs — a reasonable default for service scheduling,
     * documented in .claude/docs/assumptions.md rather than made
     * configurable, since nothing in scope calls for finer granularity.
     */
    private static final Duration SLOT_GRANULARITY = Duration.ofMinutes(15);

    private final DealershipRepository dealershipRepository;
    private final VehicleRefRepository vehicleRefRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final TechnicianRepository technicianRepository;
    private final AppointmentRepository appointmentRepository;
    private final DurationResolver durationResolver;

    public List<AvailabilitySlot> findAvailability(
            Long dealershipId, Long vehicleId, Long serviceTypeId, OffsetDateTime near, int windowHours) {
        Dealership dealership =
                dealershipRepository
                        .findById(dealershipId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Dealership " + dealershipId + " not found"));
        VehicleRef vehicle =
                vehicleRefRepository
                        .findById(vehicleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Vehicle " + vehicleId + " not found"));
        serviceTypeRepository
                .findById(serviceTypeId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Service type " + serviceTypeId + " not found"));

        Duration duration =
                Duration.ofMinutes(
                        durationResolver.resolveDurationMinutes(serviceTypeId, vehicle.getVehicleCategory()));

        List<Technician> qualifiedTechnicians =
                technicianRepository.findQualifiedActive(dealershipId, serviceTypeId);
        if (qualifiedTechnicians.isEmpty()) {
            return List.of();
        }

        OffsetDateTime windowStart = near.minusHours(windowHours);
        OffsetDateTime windowEnd = near.plusHours(windowHours);

        List<AvailabilitySlot> results = new ArrayList<>();
        OffsetDateTime candidateStart = alignToGranularity(windowStart);
        while (!candidateStart.isAfter(windowEnd)) {
            OffsetDateTime candidateEnd = candidateStart.plus(duration);
            if (BusinessHours.fits(dealership, candidateStart, candidateEnd)
                    && !appointmentRepository
                            .findFreeBayIds(dealershipId, candidateStart, candidateEnd)
                            .isEmpty()) {
                for (Technician technician : qualifiedTechnicians) {
                    if (appointmentRepository.isTechnicianFree(
                            technician.getId(), candidateStart, candidateEnd)) {
                        results.add(
                                new AvailabilitySlot(
                                        candidateStart, technician.getId(), technician.getDisplayName()));
                    }
                }
            }
            candidateStart = candidateStart.plus(SLOT_GRANULARITY);
        }
        return results;
    }

    private OffsetDateTime alignToGranularity(OffsetDateTime time) {
        OffsetDateTime flooredToMinute = time.withSecond(0).withNano(0);
        long remainder = flooredToMinute.getMinute() % SLOT_GRANULARITY.toMinutes();
        return remainder == 0
                ? flooredToMinute
                : flooredToMinute.plusMinutes(SLOT_GRANULARITY.toMinutes() - remainder);
    }
}
