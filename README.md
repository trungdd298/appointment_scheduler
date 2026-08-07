# Unified Service Scheduler

A resource-constrained appointment scheduler for a vehicle dealership's
service department. Given a vehicle, service type, dealership, and desired
time, it finds a free **ServiceBay** *and* a qualified **Technician** for
the full service duration, then creates a persistent **Appointment** —
with double-booking prevented at the database level, not just in
application code.

The design phase (concurrency model, data ownership boundaries, API
contract) is documented in full under [`.claude/docs/`](.claude/docs/) —
this README is the practical "how do I run it and how do I know it works"
companion.

## The core guarantee, in one sentence

Two customers can never be booked into the same bay or the same technician
at overlapping times, **enforced by a Postgres `EXCLUDE` constraint** —
application code does a best-effort candidate search for UX, but the
database is what actually makes double-booking impossible, even under
concurrent requests. See [Proving the guarantee](#proving-the-guarantee-yourself)
below to see this for yourself in under a minute.

## Quick start

Requires Docker, a JDK 17 toolchain (or let the Gradle wrapper provision
one), and Node.js for the frontend (see the [Node version
note](#node-version-note) below).

### Option A — everything in Docker (fastest, no local JDK/Node needed)

```bash
docker compose --profile full up -d --build
```

That's it — Postgres, the seeded backend, and the frontend all build and
start together. Open http://localhost:5173.

This is gated behind the `full` profile so it doesn't interfere with
Option B below (its `docker compose up -d`, with no profile, still starts
*only* Postgres). See `docker-compose.yml` for how the containers are
wired: the backend talks to Postgres over the internal Docker network
(`postgres:5432`), while the frontend's build bakes in
`http://localhost:8080` as its API base URL — that has to be a
browser-reachable address, not a Docker-internal service name, since the
frontend is a static bundle the *browser* calls directly, not something
the backend talks to server-side.

```bash
docker compose --profile full down   # stop everything
```

### Option B — run the backend/frontend directly (better for active development)

```bash
# 1. Start local Postgres
docker compose up -d

# 2. Run the backend, seeded with sample data
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
# → API on http://localhost:8080, seeded: 1 dealership, 3 bays,
#   3 technicians, 4 service types, 3 vehicles, 2 customers

# 3. In a second terminal, run the frontend
npm --prefix frontend install
npm --prefix frontend run dev
# → UI on http://localhost:5173
```

Either way, open http://localhost:5173 and walk through the flow: pick a
vehicle, service, and time → see real availability → hold a slot → confirm
it.

### Node version note

`frontend/` is pinned to Vite 5.x / `@vitejs/plugin-react` 4.x rather than
their current majors (Vite 8, plugin-react 6), which require Node
`^20.19 || >=22.12`. If your Node satisfies that, you can bump both — just
confirm with `npm view vite engines` / `npm view @vitejs/plugin-react
engines` first rather than assuming.

## Proving the guarantee yourself

The concurrency test is the actual evidence, not just the schema. Run it
directly:

```bash
./gradlew test --tests "com.appointment_scheduler.service.BookingConcurrencyIT"
```

It fires **20 concurrent hold requests at the exact same slot** on a
dealership with a single bay — no pre-insert locking anywhere in the
application code — and asserts exactly one succeeds while the other 19
receive a clean `409 SLOT_NO_LONGER_AVAILABLE`, with only one row
occupying the slot in the database afterward. See
[`.claude/docs/booking-flow.md`](.claude/docs/booking-flow.md#holdconfirm--implementation)
for how the retry logic works and two real concurrency bugs (a connection
pool leak and a Postgres deadlock case) that were found and fixed by this
exact test failing.

Or see it live with `curl`, once the backend is running:

```bash
# Hold and confirm an appointment
curl -X POST localhost:8080/appointments/hold -H 'Content-Type: application/json' \
  -d '{"dealershipId":1,"vehicleId":1,"customerId":1,"serviceTypeId":1,"startTime":"2026-08-10T09:00:00Z","technicianId":1}'
# → 201, note the "id" and "holdToken" in the response

curl -X POST localhost:8080/appointments/1/confirm -H 'Content-Type: application/json' \
  -d '{"holdToken":"<paste holdToken here>"}'
# → 200 CONFIRMED

# Try to book the same technician + time again
curl -i -X POST localhost:8080/appointments/hold -H 'Content-Type: application/json' \
  -d '{"dealershipId":1,"vehicleId":2,"customerId":2,"serviceTypeId":1,"startTime":"2026-08-10T09:00:00Z","technicianId":1}'
# → 409 {"error":"SLOT_NO_LONGER_AVAILABLE", ...}
```

## Running the full test suite

```bash
docker compose up -d          # some tests still need it — see note below
./gradlew clean test
```

49 tests across 10 test classes, all passing:

| Layer | Class | What it covers |
|---|---|---|
| JPA mapping | `EntityMappingIT` | The riskiest mappings: `smallint[]` arrays, native Postgres enums, DB-generated columns — verified against real Postgres, not H2 |
| Duration | `DurationResolverTest` | Override → base-duration fallback logic |
| Availability | `AvailabilityServiceIT` | Business-hours boundary, existing-appointment conflict, `resource_unavailability` conflict, unqualified-technician exclusion |
| Booking | `BookingServiceIT` | Hold/confirm happy paths, idempotency, expiry, qualification, bay-retry-on-conflict |
| **Booking** | **`BookingConcurrencyIT`** | **The double-booking guarantee — see above** |
| Reconciliation | `ResourceUnavailabilityServiceIT` | Bay auto-reassignment (with and without a replacement), technician escalation, idempotent event replay, ops reassignment |
| Seed data | `DevDataSeederIT` | Seeded row counts, idempotent re-run |
| HTTP layer | `AppointmentApiIT`, `ReferenceDataApiIT` | Real HTTP requests via MockMvc — status codes, JSON shape, error mapping |

Most tests spin up their own ephemeral Postgres via Testcontainers (Docker
must be running, but not `docker compose`). The plain Spring context test
(`AppointmentSchedulerApplicationTests`) uses the `docker-compose` instance
directly via `application.properties`, which is why that one command above
is still listed.

## What's actually implemented

- **Schema**: Flyway migrations (`src/main/resources/db/migration/`) — the
  `EXCLUDE` constraints, the two-phase Hold→Confirm state machine, and the
  resource-unavailability model are all enforced in the database itself.
- **Availability search**: `GET /dealerships/{id}/availability` — 15-minute
  candidate slots, filtered by business hours, bay availability, and
  per-technician qualification + availability.
- **Hold → Confirm booking**: server-assigned bay (never exposed to the
  client), client-chosen technician, 5-minute hold TTL with a background
  expiry sweep, idempotent confirm.
- **Resource-unavailability reconciliation**: a bay going down
  silently reassigns a `CONFIRMED` appointment to a free replacement; a
  technician going down escalates to `NEEDS_ATTENTION` for manual ops
  resolution (never silently swapped — the technician was an explicit
  customer choice), with an escalation-deadline safety net that
  auto-cancels if unresolved.
- **REST API**: matches [`.claude/docs/openapi.yaml`](.claude/docs/openapi.yaml)
  exactly, plus a small set of `GET` reference-data listing endpoints
  (dealerships/service-types/vehicles/customers) added for the frontend,
  beyond the original API design's scope.
- **Frontend**: a minimal booking flow (see [Quick start](#quick-start)) —
  demonstrates the API working, not a production UI.
- **Dev seed data**: `SPRING_PROFILES_ACTIVE=dev` inserts a deterministic
  demo dataset, safe to restart repeatedly.

Full design rationale, entity reference, and the API contract live under
[`.claude/docs/`](.claude/docs/):

| Doc | Covers |
|---|---|
| [`architecture.md`](.claude/docs/architecture.md) | Service/data ownership boundaries, concurrency design, scaling & CAP tradeoffs |
| [`booking-flow.md`](.claude/docs/booking-flow.md) | Hold→Confirm state machine, availability search, REST layer implementation, endpoint reference |
| [`data-model.md`](.claude/docs/data-model.md) | Entity reference, ownership per table, JPA mapping notes |
| [`reconciliation.md`](.claude/docs/reconciliation.md) | Resource-unavailability model, reassignment rules, escalation |
| [`assumptions.md`](.claude/docs/assumptions.md) | Every deliberate scope cut and otherwise-undocumented design decision, with reasoning |
| [`git-workflow.md`](.claude/docs/git-workflow.md) | Branch naming convention |
| [`openapi.yaml`](.claude/docs/openapi.yaml) | Full API contract |
| [`schema.sql`](.claude/docs/schema.sql) | Full DDL (consolidated copy of the Flyway migrations) |

[`CLAUDE.md`](CLAUDE.md) is the project's index/entry point for coding
agents (and a quick orientation for humans too) — start there if you want
the condensed version before diving into `.claude/docs/`.

## Project layout

```
src/main/java/com/appointment_scheduler/
├── domain/       # JPA entities + enums
├── repository/   # Spring Data repositories, incl. native tstzrange queries
├── service/      # Business logic: DurationResolver, AvailabilityService,
│                 # BookingService, ResourceUnavailabilityService, sweepers
├── web/          # REST controllers, DTOs, GlobalExceptionHandler
└── seed/         # DevDataSeeder (dev profile only)

src/main/resources/db/migration/   # Flyway migrations (source of truth for the schema)
frontend/                          # React + TypeScript booking UI
.claude/docs/                      # Full design documentation

Dockerfile, docker-compose.yml     # Backend container + full-stack orchestration
frontend/Dockerfile, nginx.conf    # Frontend container (static build served by nginx)
```
