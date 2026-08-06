package com.appointment_scheduler.service;

import com.appointment_scheduler.domain.Dealership;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;

/**
 * Shared business-hours check used by both availability search and
 * booking — the booking side must re-validate independently rather than
 * trust a prior availability read (see
 * .claude/docs/booking-flow.md#browse-then-commit), but the rule itself is
 * one piece of logic, not two.
 */
final class BusinessHours {

    private BusinessHours() {}

    /**
     * Requires the candidate to start and end within a single day's open
     * hours — an appointment crossing midnight into a different
     * open/closed day is never valid. See .claude/docs/assumptions.md #8.
     */
    static boolean fits(Dealership dealership, OffsetDateTime start, OffsetDateTime end) {
        short isoDayOfWeek = (short) start.getDayOfWeek().getValue(); // Monday = 1 .. Sunday = 7
        boolean dayOpen = Arrays.asList(dealership.getDaysOpen()).contains(isoDayOfWeek);
        if (!dayOpen) {
            return false;
        }
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();
        return !endTime.isBefore(startTime) // rejects candidates whose duration wraps past midnight
                && !startTime.isBefore(dealership.getOpensAt())
                && !endTime.isAfter(dealership.getClosesAt());
    }
}
