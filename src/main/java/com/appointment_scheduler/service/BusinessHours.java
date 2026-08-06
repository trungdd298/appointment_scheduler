package com.appointment_scheduler.service;

import com.appointment_scheduler.domain.Dealership;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

/**
 * Shared business-hours check used by both availability search and
 * booking — the booking side must re-validate independently rather than
 * trust a prior availability read (see
 * .claude/docs/booking-flow.md#browse-then-commit), but the rule itself is
 * one piece of logic, not two.
 */
final class BusinessHours {

    /**
     * "Single timezone" is a stated assumption (CLAUDE.md §5) but was never
     * pinned to a concrete zone — dealership opens_at/closes_at are wall-clock
     * times with no zone of their own. Every incoming OffsetDateTime is
     * normalized to UTC before extracting a wall-clock time or day-of-week,
     * so the result doesn't depend on which equivalent offset a client
     * happened to send (e.g. "10:00+07:00" vs "03:00Z" — the same instant).
     * See .claude/docs/assumptions.md.
     */
    private static final ZoneOffset SYSTEM_ZONE = ZoneOffset.UTC;

    private BusinessHours() {}

    /**
     * Requires the candidate to start and end within a single day's open
     * hours — an appointment crossing midnight into a different
     * open/closed day is never valid. See .claude/docs/assumptions.md #8.
     */
    static boolean fits(Dealership dealership, OffsetDateTime start, OffsetDateTime end) {
        OffsetDateTime normalizedStart = start.withOffsetSameInstant(SYSTEM_ZONE);
        OffsetDateTime normalizedEnd = end.withOffsetSameInstant(SYSTEM_ZONE);

        short isoDayOfWeek = (short) normalizedStart.getDayOfWeek().getValue(); // Monday = 1 .. Sunday = 7
        boolean dayOpen = Arrays.asList(dealership.getDaysOpen()).contains(isoDayOfWeek);
        if (!dayOpen) {
            return false;
        }
        LocalTime startTime = normalizedStart.toLocalTime();
        LocalTime endTime = normalizedEnd.toLocalTime();
        return !endTime.isBefore(startTime) // rejects candidates whose duration wraps past midnight
                && !startTime.isBefore(dealership.getOpensAt())
                && !endTime.isAfter(dealership.getClosesAt());
    }
}
