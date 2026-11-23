-- Copia local de zonas (alimentada por ZoneCreated) para asignar zona a partir de coordenadas.
CREATE TABLE zone_ref (
    zone_id   TEXT PRIMARY KEY,
    name      TEXT             NOT NULL,
    latitude  DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

-- Proyección del estado operativo de cada vehículo. La verdad está en event_store
-- (VehicleAvailabilityChanged); esta tabla se puede reconstruir con un replay.
CREATE TABLE vehicle_state (
    vehicle_id     UUID PRIMARY KEY,
    code           TEXT             NOT NULL,
    vehicle_type   TEXT             NOT NULL,
    state          TEXT             NOT NULL,
    zone_id        TEXT,
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    battery_pct    INT,
    reservation_id TEXT,
    user_id        TEXT,
    version        INT              NOT NULL DEFAULT 1,
    updated_at     TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_vehicle_state_zone ON vehicle_state (zone_id, state);
