# CLAUDE.md

## 1. Project Overview

Backend for a resource-constrained appointment scheduler. Given a vehicle,
service type, dealership, and desired time, it finds a free ServiceBay
**and** a qualified Technician for the full service duration, then creates
a persistent Appointment — with double-booking prevented at the database
level, not just in application code.

**Status:** feature-complete end to end — backend (Postgres, migrations,
entities/repositories, all domain services, REST controllers matching
`openapi.yaml` plus small `GET` reference-data listing endpoints, dev seed
data), a minimal React/TypeScript frontend under `frontend/`, a top-level
`README.md`, and Dockerfiles for both — the whole stack runs in containers
with no local JDK/Node needed (`docker compose --profile full up -d
--build`; see [deployment.md](.claude/docs/deployment.md)). Verified
against the real running stack both on the host and containerized: a
concurrency test proving the double-booking guarantee, and a full
browser-driven walkthrough (form → availability → hold → confirm →
confirmed, plus a genuine 409 double-booking rejection).

## 2. Tech Stack

- Java 17, Spring Boot 4.1.0 (confirmed buildable; note Boot 4's starter
  split — `spring-boot-starter-web` vs `spring-boot-starter-webmvc` — and
  its per-module `-test` starters, e.g. `spring-boot-starter-data-jpa-test`)
- Gradle (wrapper included, no local install needed)
- PostgreSQL 14+ (requires `btree_gist` and `pgcrypto` extensions)
- Spring Data JPA, Flyway (schema migrations)
- Testcontainers + JUnit 5 for integration tests (versions managed by
  Spring Boot's dependency-management BOM — note Testcontainers 2.x
  renamed modules, e.g. `org.testcontainers:testcontainers-postgresql`,
  not `org.testcontainers:postgresql`)
- **Jackson 3** (`tools.jackson:jackson-databind`, package
  `tools.jackson.databind.*`) — not Jackson 2
  (`com.fasterxml.jackson.databind.*`), which Boot 4.1 no longer uses for
  the core databind library. `jackson-annotations` (`@JsonProperty` etc.)
  is still `com.fasterxml.jackson.annotation.*`, unchanged — only
  core/databind moved.
- Frontend: React 19 + TypeScript + Vite 5, under `frontend/` (separate
  `package.json`, not part of the Gradle build). Pinned to Vite 5.x /
  `@vitejs/plugin-react` 4.x deliberately — their current majors (Vite 8,
  plugin-react 6) require Node `^20.19 || >=22.12`; check `node --version`
  before bumping either.

## 3. Dev Commands

```bash
docker compose --profile full up -d --build   # whole stack in containers — see deployment.md
docker compose --profile full down            # stop everything
```

Gated behind `full` so it doesn't affect the plain `docker compose up -d`
below (Postgres only). For active development, run the pieces directly:

```bash
docker compose up -d                              # start local Postgres (host port 5433, see docker-compose.yml)
./gradlew build                                    # compile + package
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun       # run the API on :8080, seeded with sample data (see below)
./gradlew test                                     # unit tests + Testcontainers-backed integration tests (spin up their own ephemeral Postgres — Docker must be running, but not docker-compose)
```

`SPRING_PROFILES_ACTIVE=dev` activates `DevDataSeeder` (1 dealership, 3
bays, 3 technicians, 4 service types, 3 vehicles, 2 customers; IDs from 1),
idempotent — safe to restart. `spring.datasource.*` defaults to
`localhost:5433` (override via `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`).

```bash
npm --prefix frontend install   # first time only
npm --prefix frontend run dev   # frontend on :5173, needs the backend already running
npm --prefix frontend run build # type-check + production build
```

`VITE_API_BASE_URL` overrides the backend URL the frontend calls
(default `http://localhost:8080`).

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
  category** if a row exists in `service_type_duration_override` — see
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
- [`.claude/docs/deployment.md`](.claude/docs/deployment.md) — Docker
  Compose full-stack setup, container wiring
- [`.claude/docs/openapi.yaml`](.claude/docs/openapi.yaml) — full API
  contract
- [`.claude/docs/schema.sql`](.claude/docs/schema.sql) — full DDL,
  consolidated copy of the Flyway migrations under
  `src/main/resources/db/migration/`
