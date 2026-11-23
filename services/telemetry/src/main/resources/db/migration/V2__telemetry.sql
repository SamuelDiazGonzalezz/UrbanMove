CREATE TABLE telemetry_reading (
    reading_id  UUID PRIMARY KEY,
    vehicle_id  UUID             NOT NULL,
    battery_pct INT CHECK (battery_pct BETWEEN 0 AND 100),
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_telemetry_vehicle ON telemetry_reading (vehicle_id, recorded_at DESC);
