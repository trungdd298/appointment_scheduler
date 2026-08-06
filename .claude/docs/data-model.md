# Data Model

Reference: [`schema.sql`](schema.sql) holds the full DDL (not yet migrated —
see the note at the top of that file) — this file explains what's in it,
not a copy of it.

## Tables

| Table | Role |
|---|---|
| `dealership` | A location. **Planned addition:** `opens_at`, `closes_at`, `days_open` — not yet in the DDL; availability search needs these to bound candidate slots. |
| `service_type` | Name + base `duration_minutes`. |
| `service_type_duration_override` | **Planned addition.** `(service_type_id, vehicle_category)` → `duration_minutes`; overrides the base when a matching row exists. |
| `service_bay` | Belongs to a dealership. Never exposed to clients — see [booking-flow.md](booking-flow.md). |
| `technician` | Local projection — display name + active flag only. Real identity lives in the Staff Service. |
| `technician_service_type` | Qualification join table. "Qualified technician" = present here for the requested service type. |
| `vehicle_ref` | Local projection (VIN, make, model). **Planned addition:** `vehicle_category` (`SEDAN`/`SUV`/`TRUCK`/`VAN`) to drive duration overrides. Source of truth is the Vehicle Registry. |
| `customer_ref` | Local projection (name, phone). Source of truth is the Customer/Account Directory. |
| `resource_unavailability` | Single table for any reason a bay or technician becomes unusable — see [reconciliation.md](reconciliation.md). |
| `appointment` | Core entity, fully owned here. Holds `during` (generated `tstzrange`), `status`, `hold_token`, `expires_at`, and escalation fields. Carries the two `EXCLUDE` constraints described in [architecture.md](architecture.md). |

## Notes for schema changes

- `appointment.during` is a **generated** column
  (`tstzrange(start_time, end_time, '[)')`) — don't write to it directly,
  don't drop it; the `EXCLUDE` constraints depend on it.
- Both `EXCLUDE` constraints are scoped `WHERE status IN
  ('HELD','CONFIRMED')` — `NEEDS_ATTENTION` is deliberately excluded
  (documented trade-off: a bay can be reclaimed by another booking before
  ops finishes resolving the incident).
- All schema changes go through a new Flyway migration file — never edit an
  already-applied migration.

See also: [architecture.md](architecture.md) (ownership rationale), [assumptions.md](assumptions.md) (scope cuts).
