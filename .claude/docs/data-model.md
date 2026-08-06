# Data Model

Reference: [`schema.sql`](schema.sql) holds the full DDL — this file
explains what's in it, not a copy of it. Migrated via Flyway
(`src/main/resources/db/migration/V1__initial_schema.sql` +
`V2__dealership_hours_and_duration_override.sql`); verified against a real
Postgres 16 instance.

## Tables

| Table | Role |
|---|---|
| `dealership` | A location. `opens_at`, `closes_at`, `days_open` (ISO weekday ints, Monday=1..Sunday=7) bound availability search — all three are `NOT NULL` with no default, so every dealership must declare its hours explicitly. |
| `service_type` | Name + base `duration_minutes`. |
| `service_type_duration_override` | `(service_type_id, vehicle_category)` → `duration_minutes`; overrides the base when a matching row exists. |
| `service_bay` | Belongs to a dealership. Never exposed to clients — see [booking-flow.md](booking-flow.md). |
| `technician` | Local projection — display name + active flag only. Real identity lives in the Staff Service. |
| `technician_service_type` | Qualification join table. "Qualified technician" = present here for the requested service type. |
| `vehicle_ref` | Local projection (VIN, make, model). `vehicle_category` (`SEDAN`/`SUV`/`TRUCK`/`VAN`, **nullable** — a synced row may not have one yet) drives duration overrides. Source of truth is the Vehicle Registry. |
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

## JPA mapping notes

Entities live under `com.appointment_scheduler.domain`, one file per table,
plus enums (`AppointmentStatus`, `ResourceType`, `VehicleCategory`,
`UnavailabilitySource`) matching the CHECK-constraint/native-enum values.
Repositories under `com.appointment_scheduler.repository` are plain
`JpaRepository`s for now — no custom query methods yet (those land with the
availability/booking services).

- The generated `during` range columns (`appointment`, `resource_unavailability`)
  are **not mapped** on either entity — range-overlap queries use `during`
  directly via native SQL, not through a Java property.
- `dealership.days_open` (`smallint[]`) maps to `Short[]` via
  `@JdbcTypeCode(SqlTypes.ARRAY)`.
- `vehicle_ref.vehicle_category` and
  `service_type_duration_override.vehicle_category` (native Postgres enum
  type) map via `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` — plain
  `@Enumerated(EnumType.STRING)` alone is only correct for the `TEXT` +
  `CHECK`-constraint columns (`appointment.status`,
  `resource_unavailability.resource_type`/`source`).
- `hold_token`, `created_at`, `updated_at` are `insertable = false,
  updatable = false` — Postgres generates/maintains them (default value,
  trigger); the in-memory entity only has real values after a fresh
  `findById`, not immediately after `save()`.
- Verified with `src/test/java/.../repository/EntityMappingIT.java`
  (Testcontainers-backed, not H2 — the schema is Postgres-specific).

See also: [architecture.md](architecture.md) (ownership rationale), [assumptions.md](assumptions.md) (scope cuts).
