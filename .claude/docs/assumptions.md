# Documented Assumptions & Scope Cuts

Explicit, deliberate scope decisions — not oversights. Don't "fix" these
without discussing first (see [`CLAUDE.md`](../../CLAUDE.md) §5).

1. **Duration resolution:** effective duration comes from
   `service_type_duration_override(service_type_id, vehicle_category)` if a
   matching row exists, else falls back to `service_type.duration_minutes`.
   The client never supplies an arbitrary duration. See
   [booking-flow.md](booking-flow.md#duration-resolution).
2. **"Qualified technician"** = present in `technician_service_type` for
   the requested service type. No finer-grained skill model.
3. **Individual technician working hours/shifts are not modeled.**
   Availability search only checks dealership business hours + existing
   bookings/unavailability, not a per-technician shift calendar.
4. **Cross-dealership double-booking is not prevented.** A vehicle could
   theoretically have overlapping appointments at two different
   dealerships — the requirements don't call for preventing this, so it
   isn't implemented. (Same-dealership double-booking of a bay or
   technician *is* prevented — see [architecture.md](architecture.md).)
5. **Hold TTL is a fixed 5 minutes**, not configurable per dealership.
6. **Full auto-reassignment and customer notification are designed only,
   not implemented.** See
   [reconciliation.md](reconciliation.md#whats-not-implemented).
7. **Availability candidates are stepped at 15-minute increments.** Not
   specified anywhere in the requirements; a reasonable default for service
   scheduling. Not configurable — nothing in scope calls for finer
   granularity. See `AvailabilityService.SLOT_GRANULARITY`.
8. **An appointment must start and end within a single day's open hours.**
   A candidate whose duration would cross midnight into a different
   open/closed day is never offered, rather than checking the next day's
   hours too. Reasonable given service-appointment durations are minutes to
   a few hours. See `BusinessHours.fits`.
9. **Bay retry is bounded at 10 candidate bays per hold attempt.** Not
   specified beyond the design's "bounded number of times" — 10 is
   well above any realistic per-dealership bay count, so it only limits
   pathological cases. See `BookingService.MAX_BAY_RETRY_ATTEMPTS`.
10. **`escalation_deadline` = the appointment's own `start_time`.** Design
    decisions say only "near the appointment time," without an exact
    offset — using `start_time` itself means an unresolved
    `NEEDS_ATTENTION` appointment auto-cancels by the moment the customer
    would have shown up, rather than proactively earlier. Customer
    notification is out of scope regardless (see #6), so the exact timing
    matters less. See `ResourceUnavailabilityService.markNeedsAttention`.
11. **A bay-unavailability reconciliation that finds no replacement bay
    also escalates to `NEEDS_ATTENTION`.** Not covered by the design docs,
    which only describe the "replacement found" case — leaving a
    `CONFIRMED` appointment pointed at a bay that's now unavailable would
    be actively wrong, so it falls back to the same ops-escalation path
    technician unavailability uses. See
    `ResourceUnavailabilityService.reconcileBayUnavailability`.
12. **"Single timezone" (an existing assumption) is pinned to UTC for
    business-hours purposes.** `dealership.opens_at`/`closes_at` are plain
    wall-clock times with no zone of their own; every incoming
    `OffsetDateTime` is normalized to UTC before extracting a wall-clock
    time or day-of-week, so the result doesn't depend on which equivalent
    offset a client sends for the same instant (e.g. `10:00+07:00` vs
    `03:00Z`). This was a real bug, not just an unstated assumption — found
    via `AppointmentApiIT`: requests that go through actual JSON
    (de)serialization behave differently from `OffsetDateTime` objects
    built directly in Java, because Jackson normalizes the offset on
    deserialization by default. Every prior test happened to pass only
    because the JVM's default zone offset matched what those tests
    constructed directly. See `BusinessHours.SYSTEM_ZONE`.
