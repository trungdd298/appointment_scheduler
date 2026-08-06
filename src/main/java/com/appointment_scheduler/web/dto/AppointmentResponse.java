package com.appointment_scheduler.web.dto;

import com.appointment_scheduler.domain.Appointment;
import com.appointment_scheduler.domain.AppointmentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentResponse(
        Long id,
        Long dealershipId,
        AppointmentStatus status,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        Long vehicleId,
        String vin,
        Long customerId,
        Long serviceTypeId,
        String serviceTypeName,
        Long bayId,
        String bayLabel,
        Long technicianId,
        String technicianName,
        UUID holdToken,
        OffsetDateTime expiresAt,
        String needsAttentionReason) {

    /**
     * {@code appointment} must have all associations loaded (e.g. via
     * AppointmentRepository#findByIdWithAssociations) — this reads
     * bay/technician/vehicle/customer/serviceType directly.
     */
    public static AppointmentResponse from(Appointment appointment) {
        // holdToken/expiresAt are DB-populated regardless of status, but per
        // the API contract are only meaningful (and only returned) while HELD.
        boolean held = appointment.getStatus() == AppointmentStatus.HELD;
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getDealership().getId(),
                appointment.getStatus(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getVehicle().getId(),
                appointment.getVehicle().getVin(),
                appointment.getCustomer().getId(),
                appointment.getServiceType().getId(),
                appointment.getServiceType().getName(),
                appointment.getBay().getId(),
                appointment.getBay().getLabel(),
                appointment.getTechnician().getId(),
                appointment.getTechnician().getDisplayName(),
                held ? appointment.getHoldToken() : null,
                held ? appointment.getExpiresAt() : null,
                appointment.getNeedsAttentionReason());
    }
}
