# Resource Unavailability & Reconciliation

## Model

One table, `resource_unavailability` (`resource_type` = `BAY`|`TECHNICIAN`,
`resource_id`, time range, `source`, `external_event_id`), used for every
cause — a technician calling in sick, a bay breaking down, HR terminating
someone, etc. `external_event_id` is the idempotency key for events
arriving from external systems.

## Reconciliation rule (differs by resource type — deliberate, not arbitrary)

| Resource | On becoming unavailable after `CONFIRMED` |
|---|---|
| Bay | **Auto-reassigned silently.** The bay was never a customer choice. |
| Technician | **Moves to `NEEDS_ATTENTION`.** Requires a human (ops) to resolve via `POST /appointments/{id}/reassign-technician`. The technician *was* an explicit customer choice (see [booking-flow.md](booking-flow.md)) — never silently swap it. |

## Safety net

A `NEEDS_ATTENTION` appointment carries an `escalation_deadline`. If it
isn't resolved by that deadline, the system automatically transitions it to
`CANCELLED` — so the customer is never left showing up to a dealership with
no one available. (Event publishing is not implemented — per
[architecture.md](architecture.md), events are simulated/mocked; no real
broker exists.)

## What's not implemented

Full auto-reassignment logic (finding and validating a replacement
technician automatically) and real customer notification are **designed
only**, not implemented, within the current scope. Only detection
(unavailability → `NEEDS_ATTENTION`) and the manual ops resolution path are
in scope. See [assumptions.md](assumptions.md).

## Implementation

`ResourceUnavailabilityService`:

- **`recordUnavailability`** — idempotent via `external_event_id` (a
  replayed event returns the existing row, no double-reconciliation).
  Inserts the `resource_unavailability` row, then reconciles:
  - **Bay:** `AppointmentRepository.findConfirmedByBayOverlapping` finds
    affected `CONFIRMED` appointments; for each, `findFreeBayIds` (the same
    query booking uses) looks for a replacement — the just-flushed
    unavailability row already excludes the now-unavailable bay, within
    the same transaction. Found → silently reassign. **Not found** → also
    escalate to `NEEDS_ATTENTION` (not explicitly covered by the design
    docs; leaving a `CONFIRMED` appointment pointed at an unavailable bay
    would be actively wrong — see
    [assumptions.md](assumptions.md)).
  - **Technician:** `findConfirmedByTechnicianOverlapping` finds affected
    appointments; each moves straight to `NEEDS_ATTENTION` with
    `escalation_deadline` set to the appointment's own `start_time` (not
    pinned to an exact offset in the design docs beyond "near the
    appointment time" — see [assumptions.md](assumptions.md)).
- **`reassignTechnician`** — the manual ops path: requires status
  `NEEDS_ATTENTION`, validates the new technician is qualified (via
  `TechnicianRepository.findQualifiedActive`) and free (`isTechnicianFree`,
  reusing the same check booking uses), then transitions back to
  `CONFIRMED` and clears `needs_attention_reason`/`escalation_deadline`.
- **`EscalationSweeper`** — a 30s scheduled sweep (same pattern as
  `HoldExpirySweeper`) bulk-cancelling `NEEDS_ATTENTION` rows past their
  `escalation_deadline`.

Verified against real Postgres in `ResourceUnavailabilityServiceIT`,
including the nullable-`end` (open-ended unavailability) native queries and
both bay-reconciliation branches (replacement found / not found).
