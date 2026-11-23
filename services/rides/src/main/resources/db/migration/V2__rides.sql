CREATE TABLE zone_ref (
    zone_id   TEXT PRIMARY KEY,
    name      TEXT             NOT NULL,
    latitude  DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

CREATE TABLE vehicle_view (
    vehicle_id     UUID PRIMARY KEY,
    code           TEXT NOT NULL,
    vehicle_type   TEXT NOT NULL,
    state          TEXT NOT NULL,
    zone_id        TEXT,
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    battery_pct    INT,
    reservation_id TEXT,
    user_id        TEXT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Viaje: OPEN desde el pickup hasta el dropoff; COMPLETED cuando se registra (TripRecorded).
CREATE TABLE trip (
    trip_id             UUID PRIMARY KEY,
    vehicle_id          UUID             NOT NULL,
    vehicle_code        TEXT             NOT NULL,
    vehicle_type        TEXT             NOT NULL,
    user_id             TEXT             NOT NULL,
    reservation_id      TEXT,
    status              TEXT             NOT NULL CHECK (status IN ('OPEN', 'COMPLETED')),
    origin_zone_id      TEXT,
    origin_latitude     DOUBLE PRECISION,
    origin_longitude    DOUBLE PRECISION,
    started_at          TIMESTAMPTZ      NOT NULL,
    start_battery_pct   INT,
    destination_zone_id TEXT,
    dest_latitude       DOUBLE PRECISION,
    dest_longitude      DOUBLE PRECISION,
    finished_at         TIMESTAMPTZ,
    end_battery_pct     INT,
    duration_seconds    INT,
    distance_meters     INT,
    cost                NUMERIC(8, 2)
);

CREATE UNIQUE INDEX ux_trip_open_vehicle ON trip (vehicle_id) WHERE status = 'OPEN';
CREATE UNIQUE INDEX ux_trip_open_user ON trip (user_id) WHERE status = 'OPEN';
CREATE INDEX idx_trip_user ON trip (user_id, started_at DESC);
