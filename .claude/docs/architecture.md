# Architecture

## Service boundaries (data ownership)

Boundaries are drawn by consistency requirement, not by entity/table.

| Data | Owned here? | Why |
|---|---|---|
| `Appointment`, `ServiceBay` (operational attrs), technician scheduling profile | Yes | Directly participates in the no-double-booking invariant — needs strong consistency inside a single DB transaction |
| Vehicle identity (VIN, make, model) | No — local projection (`vehicle_ref`) | Source of truth is the Vehicle Registry (out of scope, mocked) |
| Customer identity | No — local projection (`customer_ref`) | Source of truth is the Customer/Account Directory (out of scope) |
| Technician identity (payroll, HR, access control) | No — local projection (`technician`) | Source of truth is the Staff Service (out of scope) |

Synced via simulated async events: `TechnicianHired/Updated`,
`VehicleRegistered`, `TechnicianUnavailable`. Never call another service
synchronously inside the booking transaction — only read the local
projection.

## Concurrency correctness (defense in depth)

Two independent layers protect against the classic check-then-act race (two
holds for the same slot arriving simultaneously):

1. **DB layer — mandatory, absolute.** Postgres
   `EXCLUDE USING gist (resource_id WITH =, during WITH &&)` on
   `appointment`, once for `bay_id` and once for `technician_id`, scoped to
   `status IN ('HELD','CONFIRMED')`. Atomic at the engine level — cannot be
   bypassed by an application bug.
2. **App layer — best-effort, UX only.** The booking service queries
   candidates without locking, attempts an `INSERT`, and on constraint
   violation retries with the next in-memory candidate (bounded retries)
   before returning `409 SLOT_NO_LONGER_AVAILABLE`.

Rejected alternatives:
- Optimistic locking via a version column — doesn't apply to a brand-new
  `INSERT`.
- `SELECT ... FOR UPDATE` at candidate-selection time — wasteful, since the
  target row isn't known until a candidate is chosen; would mean locking
  rows that may never be used.

**Do not** replace the `EXCLUDE` constraint with app-only locking. The
constraint is the actual guarantee; the app layer is a UX nicety on top of
it, not a substitute.

## Scaling & CAP

- App layer is stateless — scales horizontally freely, no lock/session
  state kept in memory.
- The no-double-booking invariant only needs to hold **within a single
  dealership** → sharding by `dealership_id` is natural.
- Write path (hold/confirm) favors **CP** — better to reject than to
  double-book.
- Read path (availability) favors **AP** — can be served from a read
  replica; staleness is fine because the write path always re-validates
  independently (see [booking-flow.md](booking-flow.md)).

## Why no Saga / 2PC

The Scheduler never *writes* to another service within a single request —
it only reads/caches local projections (see the ownership table above). A
Saga would only become necessary if scope expanded to something requiring
atomic writes across ≥2 independent services — e.g. a future "book with a
loaner vehicle + a payment deposit hold" feature, which would span three
services/databases and need compensating actions on partial failure. That's
out of the current requirements; noted here so it isn't reintroduced by
accident.

## Forward-compatibility

`GET /appointments?vin=...` and a published `AppointmentConfirmedEvent`
exist so a future Document Viewer service can use this service as a data
source without a schema change, even though that consumer isn't built here.

See also: [data-model.md](data-model.md) (schema), [assumptions.md](assumptions.md) (scope cuts).
