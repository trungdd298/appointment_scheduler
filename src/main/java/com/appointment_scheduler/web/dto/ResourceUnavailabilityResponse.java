package com.appointment_scheduler.web.dto;

import com.appointment_scheduler.domain.ResourceType;
import com.appointment_scheduler.domain.ResourceUnavailability;
import com.appointment_scheduler.domain.UnavailabilitySource;
import java.time.OffsetDateTime;

public record ResourceUnavailabilityResponse(
        Long id,
        ResourceType resourceType,
        Long resourceId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String reason,
        UnavailabilitySource source,
        String externalEventId) {

    public static ResourceUnavailabilityResponse from(ResourceUnavailability u) {
        return new ResourceUnavailabilityResponse(
                u.getId(),
                u.getResourceType(),
                u.getResourceId(),
                u.getStartTime(),
                u.getEndTime(),
                u.getReason(),
                u.getSource(),
                u.getExternalEventId());
    }
}
