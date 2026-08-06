-- =====================================================================
-- Adds the two schema extensions noted as "planned" in
-- .claude/docs/data-model.md and assumptions.md:
--   1. Per-dealership business hours (availability search needs these to
--      bound candidate slots).
--   2. Vehicle-category-based duration overrides (base duration still
--      comes from service_type.duration_minutes; this only overrides it
--      when a matching row exists — see .claude/docs/booking-flow.md).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Dealership business hours
-- ---------------------------------------------------------------------

-- days_open: ISO 8601 weekday numbers, Monday = 1 .. Sunday = 7.
-- No default on any of these three columns — every dealership must
-- declare its hours explicitly rather than silently inheriting a guess.
ALTER TABLE dealership
    ADD COLUMN opens_at   TIME NOT NULL,
    ADD COLUMN closes_at  TIME NOT NULL,
    ADD COLUMN days_open  SMALLINT[] NOT NULL,
    ADD CONSTRAINT chk_dealership_hours_order CHECK (closes_at > opens_at);

-- ---------------------------------------------------------------------
-- 2. Vehicle category + duration override
-- ---------------------------------------------------------------------

CREATE TYPE vehicle_category AS ENUM ('SEDAN', 'SUV', 'TRUCK', 'VAN');

-- Nullable: a vehicle_ref row synced from the Vehicle Registry may not
-- carry a category yet. Duration resolution falls back to
-- service_type.duration_minutes whenever this is NULL or has no matching
-- override row (see .claude/docs/booking-flow.md#duration-resolution).
ALTER TABLE vehicle_ref
    ADD COLUMN vehicle_category vehicle_category;

CREATE TABLE service_type_duration_override (
    service_type_id   BIGINT NOT NULL REFERENCES service_type(id),
    vehicle_category  vehicle_category NOT NULL,
    duration_minutes  INT NOT NULL CHECK (duration_minutes > 0),
    PRIMARY KEY (service_type_id, vehicle_category)
);
