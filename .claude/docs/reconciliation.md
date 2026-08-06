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
`CANCELLED` and publishes an event — so the customer is never left showing
up to a dealership with no one available.

## What's not implemented

Full auto-reassignment logic (finding and validating a replacement
technician automatically) and real customer notification are **designed
only**, not implemented, within the current scope. Only detection
(unavailability → `NEEDS_ATTENTION`) and the manual ops resolution path are
in scope. See [assumptions.md](assumptions.md).
