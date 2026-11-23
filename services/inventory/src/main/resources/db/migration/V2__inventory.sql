CREATE TABLE vehicle_asset (
    vehicle_id    UUID PRIMARY KEY,
    code          TEXT        NOT NULL UNIQUE,
    vehicle_type  TEXT        NOT NULL CHECK (vehicle_type IN ('BICYCLE', 'ELECTRIC_BICYCLE', 'ELECTRIC_SCOOTER')),
    model         TEXT        NOT NULL,
    home_zone_id  TEXT        NOT NULL,
    purchased_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    retired_at    TIMESTAMPTZ,
    retire_reason TEXT
);
