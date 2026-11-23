CREATE TABLE incident (
    incident_id UUID PRIMARY KEY,
    vehicle_id  UUID        NOT NULL,
    reported_by TEXT        NOT NULL,
    type        TEXT        NOT NULL CHECK (type IN ('DAMAGE', 'BATTERY', 'PARKING', 'OTHER')),
    description TEXT,
    status      TEXT        NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolution  TEXT
);

CREATE INDEX idx_incident_status ON incident (status, created_at DESC);
