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

(The override table and `vehicle_ref.vehicle_category` are a planned schema
addition — not yet present in `schema.sql`. See
[data-model.md](data-model.md).)

## Endpoint reference

| Endpoint | Purpose |
|---|---|
| `GET /dealerships/{id}/availability` | Browse feasible `(time, technician)` options near a desired time |
| `POST /appointments/hold` | Reserve a specific slot; server assigns the bay |
| `POST /appointments/{id}/confirm` | Finalize a hold using its `hold_token` |
| `GET /appointments/{id}` | Fetch one appointment |
| `POST /appointments/{id}/cancel` | Cancel (customer or ops) |
| `GET /appointments?status=&dealershipId=&vin=` | List/filter — ops queue and VIN lookup |
| `POST /appointments/{id}/reassign-technician` | Ops resolves a `NEEDS_ATTENTION` appointment |
| `POST /internal/resource-unavailability` | Mocked webhook from Staff/Facility systems |

Full contract: [`openapi.yaml`](openapi.yaml).
See also: [architecture.md](architecture.md) (concurrency), [assumptions.md](assumptions.md) (scope cuts).
