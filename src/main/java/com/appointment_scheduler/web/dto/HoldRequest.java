package com.appointment_scheduler.web.dto;

import com.appointment_scheduler.service.dto.HoldCommand;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record HoldRequest(
        @NotNull Long dealershipId,
        @NotNull Long vehicleId,
        @NotNull Long customerId,
        @NotNull Long serviceTypeId,
        @NotNull OffsetDateTime startTime,
        @NotNull Long technicianId) {

    public HoldCommand toCommand() {
        return new HoldCommand(dealershipId, vehicleId, customerId, serviceTypeId, startTime, technicianId);
    }
}
