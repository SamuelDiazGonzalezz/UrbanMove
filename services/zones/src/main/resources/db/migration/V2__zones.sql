CREATE TABLE zone (
    zone_id         TEXT PRIMARY KEY,
    name            TEXT             NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    radius_meters   INT              NOT NULL,
    target_vehicles INT              NOT NULL DEFAULT 3,
    active          BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now()
);
