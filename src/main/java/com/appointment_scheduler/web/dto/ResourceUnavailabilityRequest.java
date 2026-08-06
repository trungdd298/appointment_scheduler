package com.appointment_scheduler.web.dto;

import com.appointment_scheduler.domain.ResourceType;
import com.appointment_scheduler.domain.UnavailabilitySource;
import com.appointment_scheduler.service.dto.RecordUnavailabilityCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ResourceUnavailabilityRequest(
        @NotNull ResourceType resourceType,
        @NotNull Long resourceId,
        @NotNull OffsetDateTime startTime,
        OffsetDateTime endTime, // nullable: open-ended
        String reason,
        @NotNull UnavailabilitySource source,
        @NotBlank String externalEventId) {

    public RecordUnavailabilityCommand toCommand() {
        return new RecordUnavailabilityCommand(
                resourceType, resourceId, startTime, endTime, reason, source, externalEventId);
    }
}
