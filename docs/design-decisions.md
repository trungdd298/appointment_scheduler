# Scenario A — Unified Service Scheduler: Design Decisions

This document consolidates the architecture decisions behind the Scheduler
service, and is intended as source material for the System Design Document
(Part 1). Each section states the problem, the chosen approach, and the
reasoning.

---

## 1. Concurrency correctness: defense in depth

**Problem:** two booking requests for an overlapping slot arriving almost
simultaneously can both observe "still available" (classic check-then-act
race) → double-booking.

**Solution — two independent layers of protection, each serving a different
purpose:**

- **DB layer (mandatory, absolute):** a PostgreSQL `EXCLUDE` constraint on
  `(resource_id WITH =, during WITH &&)` for both `bay_id` and
  `technician_id`. Atomic at the engine level — cannot be bypassed even if
  application code has a bug.
- **Application layer (best-effort, purely for UX):** query for "available
  candidates" without locking, attempt an `INSERT`; if rejected by the
  constraint, retry with the next candidate already held in memory (no
  re-query). Retry a bounded number of times before returning 409.

**Why not optimistic locking via a version column (rejected option B):** a
version column only makes sense when updating a row that already exists —
it does not apply to a brand-new `INSERT`. "Insert and let the EXCLUDE
constraint arbitrate" already provides an equivalent guarantee, so a
separate optimistic-locking mechanism is unnecessary.

**Why not pessimistic locking (`SELECT ... FOR UPDATE`) at the resource
selection step:** the system must search for one candidate among N options
(the target row isn't known in advance), so locking a batch of rows that
"might or might not be used" is wasteful. `FOR UPDATE` only makes sense once
the exact row to protect is already known.

---

## 2. Two-phase booking: Hold → Confirm

**UX problem:** with a single check-then-create step, the user fills out the
entire form and only discovers a conflict on submit — a poor experience if
someone else claims the slot while they were still typing.

**Solution:** split into `HELD` (5-minute TTL, reserved as soon as a slot is
selected) → `CONFIRMED` (once the user completes and confirms). The
`EXCLUDE` constraint applies to both `HELD` and `CONFIRMED` (excluding
`EXPIRED`/`CANCELLED`/`NEEDS_ATTENTION` via a partial constraint predicate
`WHERE status IN ('HELD','CONFIRMED')`).

**Idempotency:** every hold carries a `hold_token` (a separate UUID) — the
confirm step uses this token to prevent duplicate confirmation caused by
client retries.

---

## 3. Browse-then-commit: the user picks a time + technician, not a bay

**Decision:** `GET /availability` returns a list of **(startTime,
technician)** options — the Bay is never exposed (the customer has no basis
to choose a bay; the system assigns one silently), but the Technician is
exposed (the customer has a real reason to prefer a technician they trust or
know).

**Important consequence:** because the Technician is now an explicit
customer choice, **the system must not silently reassign the technician**
when handling a disruption (Section 5) — unlike the Bay, which has never
been a customer choice and can always be swapped silently.

**Avoiding TOCTOU:** the `GET /availability` step is unlocked and can be
stale the moment it returns. The `POST /appointments/hold` step always
re-runs the full candidate-search + insert-retry independently, trusting
nothing from the prior step. If a race occurs (409), the client simply calls
`GET /availability` again to refresh the list.

---

## 4. Data ownership boundaries (considering all four scenarios in the brief)

Service boundaries are drawn along **invariants that require strong
consistency**, not along entities/tables.

| Entity | Owned by | Why |
|---|---|---|
| `Appointment`, `ServiceBay` (operational attributes), `TechnicianSchedulingProfile` | **Scheduler** (this service) | Directly participates in the "no double-booking" invariant — requires strong consistency within a single DB transaction |
| Full vehicle identity (VIN, make, model...) | Vehicle Registry (out of scope, mocked) | Shared across Supply (B), Demand (C), and Operate (D) — Scheduler keeps only a thin local `vehicle_ref` (id, VIN, model) |
| Full customer identity | Customer/Account Directory (out of scope) | A Lead (C) becomes a Customer over time — the same entity, shared across domains |
| Full technician identity | Staff Service (out of scope) | Used for payroll, access control, warranty records — compliance/NFRs entirely different from scheduling |

Synced via asynchronous events (simulated — no real message broker required
for this assessment): `TechnicianHired/Updated`, `VehicleRegistered`,
`TechnicianUnavailable`. The Scheduler keeps only a **minimal local
projection** — enough to operate independently, without calling other
services during the booking flow.

**Why Saga/2PC is not needed** for the current flow: the Scheduler never
needs to *write* to another service within a single request — it only
reads/caches. A Saga would only become necessary if the scope expanded to a
case requiring atomic writes across ≥2 independent services (e.g., a future
feature: booking with a loaner vehicle reservation + a payment deposit hold
— three services, three databases, requiring compensating actions if one
step fails). This is out of the core requirements — noted under "Future
Considerations."

---

## 5. Resource unavailability & reconciliation

**Problem:** a technician calls in sick / a bay breaks down after an
appointment has already been `CONFIRMED`.

**Model:** a single `resource_unavailability` table (resource_type,
resource_id, time range, source) used for any cause (HR event,
self-reported, facility report) — avoiding three duplicated code paths.

**Reconciliation rule — differs by resource type, consistent with Section 3:**

| Resource | Auto-reassign? |
|---|---|
| Bay | **Yes, silently** — never a customer choice to begin with |
| Technician | **No** — moves to `NEEDS_ATTENTION`, requires human (ops) intervention because it was an explicit customer choice |

**Safety net:** if a `NEEDS_ATTENTION` appointment isn't resolved before an
escalation deadline near the appointment time, the system automatically
transitions it to `CANCELLED` and publishes an event — ensuring the customer
is never left showing up at the dealership with no one available.

---

## 6. Scaling

- **Application layer:** stateless, scales horizontally freely (no lock or
  session state kept in memory).
- **Database layer:** the "no double-booking" invariant only needs to hold
  **within a single dealership** — sharding by `dealership_id` is natural
  ("embarrassingly shardable"), with each transaction remaining ACID within
  its shard.
- **CAP trade-off per operation type** (not one choice for the whole
  system): the write path (hold/confirm) favors **CP** — better to reject
  than to double-book; the read path (viewing available slots) accepts
  **AP** — served from a read replica, tolerating slight staleness since the
  final write is always re-validated by the primary DB.

---

## 7. Forward-compatibility with Scenario D (Document Viewer)

In the real Keyloop system, the Scheduler is very likely one of the sources
that the Document Viewer's "Service System API" would call. The API is
designed with this in mind: `GET /appointments?vin={vin}` (read by VIN) and
a published `AppointmentConfirmedEvent` — while Scenario D itself isn't
implemented, the Scheduler is designed to be ready to serve as a data source
for it.

---

## 8. Explicitly documented assumptions (per the brief's "A Note on Ambiguity")

1. Appointment duration comes from `ServiceType.durationMinutes`; the user
   cannot specify an arbitrary duration.
2. "Qualified technician" = present in the `technician_service_type` table
   for the requested service type.
3. Individual technician working hours/shifts are not modeled (out of
   scope).
4. A vehicle could theoretically have overlapping appointments at two
   different dealerships — the brief does not require preventing this, so
   it is not implemented.
5. Hold TTL is a fixed 5 minutes, not configurable per dealership.
6. Full auto-reassignment when a bay/technician becomes unavailable
   (finding a replacement, not just detecting the conflict) and real
   customer notification: **designed only, not implemented** within the
   one-week scope — see "Future Enhancements."
