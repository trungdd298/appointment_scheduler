package com.appointment_scheduler.service.dto;

import com.appointment_scheduler.domain.ResourceType;
import com.appointment_scheduler.domain.UnavailabilitySource;
import java.time.OffsetDateTime;

/**
 * Input to ResourceUnavailabilityService.recordUnavailability — mirrors
 * the mocked POST /internal/resource-unavailability webhook payload. See
 * .claude/docs/reconciliation.md.
 */
public record RecordUnavailabilityCommand(
        ResourceType resourceType,
        Long resourceId,
        OffsetDateTime startTime,
        OffsetDateTime endTime, // null = open-ended
        String reason,
        UnavailabilitySource source,
        String externalEventId) {}
