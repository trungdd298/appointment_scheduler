package com.appointment_scheduler.service.dto;

import java.time.OffsetDateTime;

/**
 * Input to BookingService.hold. The bay is deliberately absent — the
 * server assigns it; the client only supplies the technician it chose from
 * GET /availability. See .claude/docs/booking-flow.md#browse-then-commit.
 */
public record HoldCommand(
        Long dealershipId,
        Long vehicleId,
        Long customerId,
        Long serviceTypeId,
        OffsetDateTime startTime,
        Long technicianId) {}
