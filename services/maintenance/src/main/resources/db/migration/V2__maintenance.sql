CREATE TABLE maintenance_order (
    order_id        UUID PRIMARY KEY,
    vehicle_id      UUID        NOT NULL,
    type            TEXT        NOT NULL CHECK (type IN ('CHARGE', 'REPAIR')),
    reason          TEXT        NOT NULL,
    origin          TEXT        NOT NULL CHECK (origin IN ('AUTOMATIC', 'MANUAL')),
    source_event_id TEXT UNIQUE,
    status          TEXT        NOT NULL CHECK (status IN ('OPEN', 'WITHDRAWN', 'IN_PROGRESS', 'COMPLETED', 'CLOSED', 'CANCELLED')),
    assignee        TEXT,
    return_zone_id  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ
);

-- Una sola orden abierta por vehículo.
CREATE UNIQUE INDEX ux_maintenance_open_vehicle ON maintenance_order (vehicle_id)
    WHERE status IN ('OPEN', 'WITHDRAWN', 'IN_PROGRESS', 'COMPLETED');

-- Usos desde la última revisión (alimentado por la Lambda maintenance-detection con TripRecorded).
CREATE TABLE vehicle_usage (
    vehicle_id           UUID PRIMARY KEY,
    trips_since_revision INT         NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_event (
    source_event_id TEXT PRIMARY KEY,
    vehicle_id      UUID        NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
