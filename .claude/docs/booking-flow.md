# Booking Flow

## State machine

```
 GET /availability        POST /appointments/hold      POST /appointments/{id}/confirm
 (browse, unlocked)  ──►  HELD (5-min TTL)  ──────────────────►  CONFIRMED
                             │                                       │
                     TTL expires unconfirmed              technician becomes unavailable
                             ▼                                       ▼
                          EXPIRED                          NEEDS_ATTENTION ──► CONFIRMED
                                                             (ops reassigns technician)
                                                                       │
                                                    unresolved past escalation_deadline
                                                                       ▼
                                                                  CANCELLED
```

`CANCELLED` is also reachable directly from `HELD`/`CONFIRMED` via
`POST /appointments/{id}/cancel`.

## Why two phases

A single check-then-create step means the customer fills out the entire
form and only discovers a conflict on submit. Splitting into Hold (reserve
as soon as a slot is picked) → Confirm (finalize) avoids that. `hold_token`
is a separate UUID from the appointment id, used to make `confirm`
idempotent against client retries.

## Browse-then-commit

- `GET /dealerships/{id}/availability` is **unlocked** — a pure read that
  can go stale the instant it returns. It returns `(startTime,
  technicianId)` pairs; the Bay is never included, since the server
  assigns it and the customer has no basis to choose one.
- `POST /appointments/hold` **never trusts** the prior availability read —
  it re-runs the full candidate search + insert-retry independently. On a
  409, the client just re-queries availability.

## Duration resolution

1. Look up `service_type_duration_override` for
   `(serviceTypeId, vehicle.category)`.
2. If no row matches, fall back to `service_type.duration_minutes`.
3. The client never supplies an arbitrary duration.

(See [data-model.md](data-model.md) for the override table and
`vehicle_ref.vehicle_category` column.)

Implemented as `DurationResolver` under `com.appointment_scheduler.service`.

## Availability search — implementation

`AvailabilityService.findAvailability` implements the `GET /availability`
side of browse-then-commit:

1. Resolve duration via `DurationResolver`.
2. Look up active technicians at the dealership qualified for the service
   type (`TechnicianRepository.findQualifiedActive`) — if none, return no
   slots.
3. Enumerate candidate start times in 15-minute steps across
   `near ± windowHours` (see [assumptions.md](assumptions.md) #7).
4. Keep a candidate only if it fits within the dealership's open hours for
   that single day (#8), **and** `AppointmentRepository.findFreeBayIds`
   returns at least one active bay with no overlapping `HELD`/`CONFIRMED`
   appointment or `resource_unavailability` for that window.
5. For each qualified technician, keep the `(time, technician)` pair if
   `AppointmentRepository.isTechnicianFree` also holds for that technician.

`findFreeBayIds`/`isTechnicianFree` are native queries using Postgres'
`tstzrange &&` operator directly against `during` — see
[data-model.md](data-model.md#jpa-mapping-notes) for why that column isn't
mapped as a Java property. Verified against real Postgres in
`AvailabilityServiceIT` (business-hours boundary, existing-appointment
conflict, `resource_unavailability` conflict, unqualified-technician
exclusion).

## Hold/Confirm — implementation

`BookingService` is the "best-effort, purely for UX" application layer
described in
[architecture.md](architecture.md#concurrency-correctness-defense-in-depth)
— it does no pre-insert locking. The actual guarantee is the
`no_overlap_per_bay`/`no_overlap_per_technician` `EXCLUDE` constraints;
this class only searches for a plausible candidate and lets Postgres
arbitrate.

**`hold(HoldCommand)`:**
1. Validate dealership/vehicle/customer/serviceType/technician exist (404
   `NOT_FOUND`).
2. Validate the technician is qualified via
   `TechnicianRepository.findQualifiedActive` — this single membership
   check also confirms the technician belongs to the requested dealership,
   so there's no separate lazy `technician.getDealership()` access (422
   `TECHNICIAN_NOT_QUALIFIED` otherwise).
3. Resolve duration, check business hours (`VALIDATION_ERROR` if outside).
4. Check the technician is free (`SLOT_NO_LONGER_AVAILABLE` if not) — the
   technician is the client's explicit choice, so unlike the bay there's no
   retry against a different technician.
5. Enumerate candidate bay IDs via `findFreeBayIds`, then attempt an
   `INSERT` per candidate, each in its **own `REQUIRES_NEW` transaction**
   (bounded at `MAX_BAY_RETRY_ATTEMPTS`), catching
   `DataAccessException` broadly (a clean `EXCLUDE` violation is
   `DataIntegrityViolationException`, but Postgres can also report a
   genuine `deadlock_detected` — `CannotAcquireLockException` — while
   checking the constraint under heavy concurrency; both mean "this
   attempt didn't work," so both just move to the next candidate) and
   moving to the next candidate on failure. `SLOT_NO_LONGER_AVAILABLE` if
   every attempt fails.

**Why `REQUIRES_NEW` per attempt, not a shared transaction:** Postgres
aborts a transaction outright after *any* SQL error, including an
`EXCLUDE` constraint violation — reusing one transaction across attempts
would make every attempt after the first fail regardless of whether that
candidate bay was actually free. Each attempt therefore runs via a
`TransactionTemplate` (programmatic, not `@Transactional` on a sibling
method — a same-bean call would silently skip the AOP proxy and the new
propagation would never take effect).

**Why `hold()` itself isn't `@Transactional`:** wrapping the outer method
would hold one pooled connection for its whole duration while each
`REQUIRES_NEW` attempt inside it checks out a second — doubling connection
usage for no correctness benefit (the reads don't need a consistent
snapshot; the constraint is the real guarantee). This was caught by
`BookingConcurrencyIT` exhausting the connection pool before the fix.

**`confirm(appointmentId, holdToken)`:** token match (404
`HOLD_TOKEN_MISMATCH`) → idempotent no-op if already `CONFIRMED` → 410
`HOLD_EXPIRED` if not `HELD` or past `expires_at` (lazily transitioning to
`EXPIRED` first). That status write uses
`@Transactional(noRollbackFor = HoldExpiredException.class)` — Spring
rolls back the whole method on any unchecked exception by default, which
would otherwise silently discard the `EXPIRED` write right before the
throw.

**Hold-expiry sweep:** `HoldExpirySweeper` runs
`AppointmentRepository.expireStaleHolds` (a bulk update) every 30s — a
`HELD` row past its TTL still satisfies the `EXCLUDE` constraints (they
don't check `expires_at`), so this sweep is what actually releases the
slot for other bookers.

**Concurrency proof:** `BookingConcurrencyIT` fires 20 concurrent
`hold()` calls at the exact same slot on a dealership with a single bay —
exactly one wins, the other 19 get `SlotNoLongerAvailableException`, and
only one row occupies the slot in the database afterward. This is the
actual evidence for the double-booking guarantee, not just the schema.

## Endpoint reference

| Endpoint | Controller | Purpose |
|---|---|---|
| `GET /dealerships` | `ReferenceDataController` | List dealerships — not in the original scope, added for FE pickers (see below) |
| `GET /service-types` | `ReferenceDataController` | List service types |
| `GET /vehicles` | `ReferenceDataController` | List vehicles |
| `GET /customers` | `ReferenceDataController` | List customers |
| `GET /dealerships/{id}/availability` | `AvailabilityController` | Browse feasible `(time, technician)` options near a desired time |
| `POST /appointments/hold` | `AppointmentController` | Reserve a specific slot; server assigns the bay |
| `POST /appointments/{id}/confirm` | `AppointmentController` | Finalize a hold using its `hold_token` |
| `GET /appointments/{id}` | `AppointmentController` | Fetch one appointment |
| `POST /appointments/{id}/cancel` | `AppointmentController` | Cancel (customer or ops); only from `HELD`/`CONFIRMED` |
| `GET /appointments?status=&dealershipId=&vin=` | `AppointmentController` | List/filter — ops queue and VIN lookup |
| `POST /appointments/{id}/reassign-technician` | `OpsController` | Ops resolves a `NEEDS_ATTENTION` appointment |
| `POST /internal/resource-unavailability` | `IntegrationController` | Mocked webhook from Staff/Facility systems |

Full contract: [`openapi.yaml`](openapi.yaml).
See also: [architecture.md](architecture.md) (concurrency), [assumptions.md](assumptions.md) (scope cuts).

## REST layer — implementation

Request/response DTOs live under `com.appointment_scheduler.web.dto`, one
record per `openapi.yaml` schema — field-for-field, including the rule that
`holdToken`/`expiresAt` are only populated in `AppointmentResponse` while
`status = HELD` (the underlying DB columns are populated regardless; the
DTO mapping nulls them out otherwise, per the documented API contract).
`GET /availability` reuses `service.dto.AvailabilitySlot` directly rather
than a separate web DTO — the shapes are already identical.

`ReferenceDataController` (`GET /dealerships`, `/service-types`,
`/vehicles`, `/customers`) is a small, deliberate scope addition beyond the
original scenario brief: the booking flow otherwise requires the client to
already know raw dealership/vehicle/serviceType/customer IDs, which a real
frontend can't do without *some* way to list them. Plain `findAll()` reads,
no new business logic — see `WebConfig` for the CORS config that lets the
local Vite dev server call them.

`GlobalExceptionHandler` (`@RestControllerAdvice`) is the single place
mapping every service-layer exception to the documented
`(HTTP status, error code)` pair from the `ErrorResponse` schema — see the
class for the full table. Bean-validation failures
(`MethodArgumentNotValidException`, `ConstraintViolationException`,
`MissingServletRequestParameterException`) and malformed JSON
(`HttpMessageNotReadableException`) all map to `400 VALIDATION_ERROR`.

`POST /internal/resource-unavailability` needs to return `201` on a new
event and `200` on a replay (per `openapi.yaml`), but
`ResourceUnavailabilityService.recordUnavailability` only returns the row,
not whether it was new — `IntegrationController` does its own
`findByExternalEventId` check first purely to pick the status code, ahead
of the service's own (separately correct) idempotency check.

**Bug found via `AppointmentApiIT`, not caught by any service-layer test:**
requests that go through actual JSON serialization behave differently from
`OffsetDateTime` objects built directly in Java in a test — Jackson
normalizes the offset on deserialization by default, and `BusinessHours`
was reading wall-clock time off the *raw* offset. Every prior test passed
only by coincidence (the JVM's default zone offset matched what those
tests constructed directly). Fixed by normalizing to a fixed zone (UTC)
before any wall-clock extraction — see
[assumptions.md](assumptions.md) #12. This is exactly why the API layer
needs its own tests that go through real (de)serialization, not just
service-layer tests that never leave Java objects.

Verified end-to-end (real HTTP requests via `MockMvc`, real Postgres via
Testcontainers) in `AppointmentApiIT`: one happy-path test per endpoint
plus the HTTP status mapping for each error case.
