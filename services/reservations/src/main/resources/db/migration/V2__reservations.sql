-- Copia local del estado de los vehículos (VehicleAvailabilityChanged). Puede ir con retraso:
-- availability valida de nuevo y, si el vehículo ya no está libre, publica VehicleReservationRejected.
CREATE TABLE vehicle_view (
    vehicle_id     UUID PRIMARY KEY,
    code           TEXT NOT NULL,
    vehicle_type   TEXT NOT NULL,
    state          TEXT NOT NULL,
    zone_id        TEXT,
    battery_pct    INT,
    reservation_id TEXT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation (
    reservation_id UUID PRIMARY KEY,
    vehicle_id     UUID        NOT NULL,
    vehicle_code   TEXT        NOT NULL,
    user_id        TEXT        NOT NULL,
    zone_id        TEXT,
    status         TEXT        NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    ended_at       TIMESTAMPTZ,
    end_reason     TEXT
);

-- Regla de negocio: un vehículo no puede tener dos reservas activas, ni un usuario tampoco.
CREATE UNIQUE INDEX ux_reservation_active_vehicle ON reservation (vehicle_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_reservation_active_user ON reservation (user_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_reservation_user ON reservation (user_id, created_at DESC);

CREATE TABLE reservation_attempt (
    attempt_id UUID PRIMARY KEY,
    vehicle_id UUID,
    user_id    TEXT        NOT NULL,
    zone_id    TEXT,
    result     TEXT        NOT NULL,
    reason     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
