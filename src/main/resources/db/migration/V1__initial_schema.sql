-- =====================================================================
-- Scenario A — Unified Service Scheduler
-- Initial schema. See .claude/docs/architecture.md, data-model.md, and
-- reconciliation.md for the reasoning behind each decision.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- required for mixed EXCLUDE (= and &&)
CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- for gen_random_uuid()

-- ---------------------------------------------------------------------
-- Reference / lookup tables
-- ---------------------------------------------------------------------

CREATE TABLE dealership (
    id    BIGSERIAL PRIMARY KEY,
    name  TEXT NOT NULL
);

CREATE TABLE service_type (
    id                BIGSERIAL PRIMARY KEY,
    name              TEXT NOT NULL,               -- "Oil Change"
    duration_minutes  INT  NOT NULL CHECK (duration_minutes > 0)
);

CREATE TABLE service_bay (
    id             BIGSERIAL PRIMARY KEY,
    dealership_id  BIGINT NOT NULL REFERENCES dealership(id),
    label          TEXT NOT NULL,                  -- "Bay #3"
    is_active      BOOLEAN NOT NULL DEFAULT true
);

-- Local projection: full identity (real name, salary, HR status) is owned
-- by the Staff Service (out of scope). This table holds only what the
-- Scheduler needs to operate on its own, synced via TechnicianHired/Updated
-- events (mocked).
CREATE TABLE technician (
    id             BIGSERIAL PRIMARY KEY,           -- mirrors the external id
    dealership_id  BIGINT NOT NULL REFERENCES dealership(id),
    display_name   TEXT NOT NULL,                   -- cached, shown to customers when choosing
    is_active      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE technician_service_type (
    technician_id    BIGINT NOT NULL REFERENCES technician(id),
    service_type_id  BIGINT NOT NULL REFERENCES service_type(id),
    PRIMARY KEY (technician_id, service_type_id)
);

-- Local projection: the source of truth is the Vehicle Registry (shared
-- across Supply/Demand/Operate domains), out of scope.
CREATE TABLE vehicle_ref (
    id     BIGSERIAL PRIMARY KEY,
    vin    TEXT NOT NULL UNIQUE,
    make   TEXT,
    model  TEXT
);

-- Local projection: the source of truth is the Customer/Account Directory,
-- out of scope.
CREATE TABLE customer_ref (
    id     BIGSERIAL PRIMARY KEY,
    name   TEXT NOT NULL,
    phone  TEXT
);

-- ---------------------------------------------------------------------
-- Resource unavailability — a single source for any reason a resource
-- becomes unusable (technician out sick, bay out of service, ...),
-- regardless of where it originates.
-- ---------------------------------------------------------------------

CREATE TABLE resource_unavailability (
    id                  BIGSERIAL PRIMARY KEY,
    resource_type       TEXT NOT NULL CHECK (resource_type IN ('BAY','TECHNICIAN')),
    resource_id         BIGINT NOT NULL,
    start_time          TIMESTAMPTZ NOT NULL,
    end_time            TIMESTAMPTZ,                -- NULL = open-ended (e.g. terminated employment)
    during              TSTZRANGE GENERATED ALWAYS AS
                            (tstzrange(start_time, COALESCE(end_time, 'infinity'), '[)')) STORED,
    reason              TEXT,
    source              TEXT NOT NULL,               -- 'STAFF_SYSTEM' | 'TECHNICIAN_SELF' | 'MANAGER_MANUAL' | 'FACILITY_SYSTEM'
    external_event_id   TEXT UNIQUE,                 -- idempotency key for events from external systems
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_unavail_resource ON resource_unavailability (resource_type, resource_id);
CREATE INDEX idx_unavail_during   ON resource_unavailability USING gist (during);

-- ---------------------------------------------------------------------
-- Appointment — core entity, 100% owned by the Scheduler.
-- ---------------------------------------------------------------------

CREATE TABLE appointment (
    id               BIGSERIAL PRIMARY KEY,
    dealership_id    BIGINT NOT NULL REFERENCES dealership(id),
    bay_id           BIGINT NOT NULL REFERENCES service_bay(id),
    technician_id    BIGINT NOT NULL REFERENCES technician(id),
    vehicle_id       BIGINT NOT NULL REFERENCES vehicle_ref(id),
    customer_id      BIGINT NOT NULL REFERENCES customer_ref(id),
    service_type_id  BIGINT NOT NULL REFERENCES service_type(id),

    start_time       TIMESTAMPTZ NOT NULL,
    end_time         TIMESTAMPTZ NOT NULL,
    during           TSTZRANGE GENERATED ALWAYS AS
                         (tstzrange(start_time, end_time, '[)')) STORED,

    -- HELD: temporarily reserved, awaiting user confirmation within the TTL (expires_at)
    -- CONFIRMED: confirmed
    -- NEEDS_ATTENTION: technician became unavailable after CONFIRMED, awaiting ops action
    -- EXPIRED: HELD past its TTL without being confirmed
    -- CANCELLED: cancelled (proactively, or automatically after the escalation deadline with no resolution)
    status           TEXT NOT NULL CHECK (
                         status IN ('HELD','CONFIRMED','NEEDS_ATTENTION','EXPIRED','CANCELLED')
                     ),
    expires_at       TIMESTAMPTZ,                    -- set only when status = HELD
    hold_token       UUID NOT NULL DEFAULT gen_random_uuid(),  -- idempotency key for confirm

    -- lightweight audit fields for reconciliation (ops resolving NEEDS_ATTENTION)
    needs_attention_reason  TEXT,
    escalation_deadline     TIMESTAMPTZ,              -- set when transitioning to NEEDS_ATTENTION

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_time_order CHECK (end_time > start_time)
);

-- Guard #1 (absolute, DB-level): a bay cannot have two overlapping
-- appointments (HELD/CONFIRMED).
-- NEEDS_ATTENTION is excluded from the predicate: an appointment awaiting
-- incident resolution no longer validly "occupies" the slot (documented
-- trade-off: the bay could be claimed by another appointment before ops
-- finishes resolving it — acceptable since the root cause is typically a
-- technician issue, not a bay issue).
ALTER TABLE appointment ADD CONSTRAINT no_overlap_per_bay
EXCLUDE USING gist (
    bay_id WITH =,
    during WITH &&
) WHERE (status IN ('HELD','CONFIRMED'));

-- Guard #2: same for technician.
ALTER TABLE appointment ADD CONSTRAINT no_overlap_per_technician
EXCLUDE USING gist (
    technician_id WITH =,
    during WITH &&
) WHERE (status IN ('HELD','CONFIRMED'));

CREATE INDEX idx_appointment_vehicle           ON appointment(vehicle_id);
CREATE INDEX idx_appointment_dealership_status ON appointment(dealership_id, status);
CREATE INDEX idx_appointment_technician        ON appointment(technician_id);
CREATE INDEX idx_appointment_bay               ON appointment(bay_id);
CREATE INDEX idx_appointment_expires
    ON appointment(expires_at) WHERE status = 'HELD';       -- cleanup of expired HELD rows
CREATE INDEX idx_appointment_needs_attention
    ON appointment(dealership_id, escalation_deadline) WHERE status = 'NEEDS_ATTENTION';

-- ---------------------------------------------------------------------
-- Convenience trigger: auto-update updated_at
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_appointment_updated_at
BEFORE UPDATE ON appointment
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
