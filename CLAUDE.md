# CLAUDE.md

## 1. Project Overview

Backend for a resource-constrained appointment scheduler (Keyloop take-home,
"Scenario A — Unified Service Scheduler"). Given a vehicle, service type,
dealership, and desired time, it finds a free ServiceBay **and** a qualified
Technician for the full service duration, then creates a persistent
Appointment — with double-booking prevented at the database level, not just
in application code.

**Status:** design phase complete (`docs/`), implementation not started yet.
`build.gradle` currently references invalid Spring Boot artifact coordinates
and will not build — this is the first pending task. No migrations,
entities, controllers, or frontend exist yet.

## 2. Tech Stack

- Java 17, Spring Boot 3.x (current `build.gradle` incorrectly pins `4.1.0`
  and non-existent starters — pending fix)
- Gradle (wrapper included, no local install needed)
- PostgreSQL 14+ (requires `btree_gist` and `pgcrypto` extensions)
- Spring Data JPA, Flyway (schema migrations)
- Testcontainers + JUnit 5 for integration tests (planned, not yet added)
- Frontend (planned, not yet scaffolded): React + TypeScript + Vite

## 3. Dev Commands

Not yet functional end-to-end — this is the target workflow once the
pending build fix and initial migrations land.

```bash
./gradlew build     # compile + run tests
./gradlew bootRun   # run the API locally (needs Postgres on localhost)
./gradlew test      # unit + integration tests (Testcontainers provides its own Postgres)
```

No `docker-compose.yml` exists yet for local Postgres — add it alongside the
build fix.

## 4. Core Logic Summary

- **Double-booking prevention is DB-enforced, not app-enforced.** A
  Postgres `EXCLUDE` constraint on `(resource_id, during)` for both `bay_id`
  and `technician_id` makes overlapping bookings impossible to insert,
  regardless of app-layer bugs. App code only does a best-effort candidate
  search + retry, purely for UX.
- **Booking is two-phase: Hold → Confirm.** Selecting a slot creates a
  `HELD` row (5-minute TTL); the client confirms with a `hold_token` to
  reach `CONFIRMED`. Avoids "fill out the whole form, then discover a
  conflict."
- **Bay is server-assigned and hidden from the client; Technician is
  client-chosen.** `GET /availability` returns `(startTime, technicianId)`
  pairs, never bays.
- **Duration = `ServiceType.durationMinutes`, overridden per vehicle
  category** if a row exists in `service_type_duration_override`. Planned
  addition, not yet migrated — see
  [data-model.md](.claude/docs/data-model.md).
- **Resource-unavailability reconciliation differs by resource type.** Bay
  becomes unavailable after confirmation → silently reassigned. Technician
  becomes unavailable → appointment moves to `NEEDS_ATTENTION`, requires
  explicit ops action (never auto-reassigned — it was an explicit customer
  choice).

Full rationale: see the domain docs linked in §6.

## 5. Key Constraints

Do not do any of the following without discussing first:

- Remove or weaken the `no_overlap_per_bay` / `no_overlap_per_technician`
  `EXCLUDE` constraints, or treat app-level locking as a replacement for
  them — the DB constraint is the sole correctness guarantee.
- Expose `bay_id` to API clients or let a client choose a bay.
- Auto-reassign a technician on a `CONFIRMED` appointment. Must go through
  `NEEDS_ATTENTION` + the ops `reassign-technician` endpoint.
- Build full auth, customer identity, or technician identity/payroll
  systems in this service. `vehicle_ref`, `customer_ref`, `technician` are
  intentionally thin local projections — their real owners (Vehicle
  Registry, Customer/Account Directory, Staff Service) are out of scope.
- Assume multiple timezones across dealerships — single timezone is a
  stated assumption.
- Make the 5-minute hold TTL configurable per dealership — it's fixed by
  design.
- Introduce Saga/2PC — not needed under the current scope (no cross-service
  atomic writes).
- Hand-edit the Postgres schema outside a Flyway migration.

- Never commit directly to `main`/`master`. Always branch first — naming
  convention in [git-workflow.md](.claude/docs/git-workflow.md).

## 6. Additional Documentation

- [`.claude/docs/architecture.md`](.claude/docs/architecture.md) — service/data
  ownership boundaries, concurrency design, scaling & CAP tradeoffs
- [`.claude/docs/booking-flow.md`](.claude/docs/booking-flow.md) —
  Hold→Confirm state machine, availability search, API endpoint reference
- [`.claude/docs/data-model.md`](.claude/docs/data-model.md) — entity
  reference, ownership per table, duration resolution
- [`.claude/docs/reconciliation.md`](.claude/docs/reconciliation.md) —
  resource-unavailability model, reassignment rules, escalation
- [`.claude/docs/assumptions.md`](.claude/docs/assumptions.md) — documented
  scope cuts and assumptions
- [`.claude/docs/git-workflow.md`](.claude/docs/git-workflow.md) — branch
  naming convention and workflow
- [`.claude/docs/openapi.yaml`](.claude/docs/openapi.yaml) — full API
  contract
- [`.claude/docs/schema.sql`](.claude/docs/schema.sql) — full DDL (not yet
  migrated; pending: dealership hours, `vehicle_category`, duration
  override table)
