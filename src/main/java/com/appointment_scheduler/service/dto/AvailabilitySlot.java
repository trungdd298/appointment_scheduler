package com.appointment_scheduler.service.dto;

import java.time.OffsetDateTime;

/**
 * A feasible (time, technician) option — never a bay, which is
 * server-assigned and hidden from the client. See
 * .claude/docs/booking-flow.md#browse-then-commit.
 */
public record AvailabilitySlot(OffsetDateTime startTime, Long technicianId, String technicianName) {}
