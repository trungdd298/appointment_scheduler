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
