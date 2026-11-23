CREATE TABLE relocation_order (
    order_id          UUID PRIMARY KEY,
    from_zone_id      TEXT        NOT NULL,
    to_zone_id        TEXT        NOT NULL,
    quantity          INT         NOT NULL CHECK (quantity > 0),
    status            TEXT        NOT NULL CHECK (status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    origin            TEXT        NOT NULL CHECK (origin IN ('AUTOMATIC', 'MANUAL')),
    reason            TEXT,
    source_event_id   TEXT UNIQUE,
    assignee          TEXT,
    moved_vehicle_ids TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ
);

-- Una sola orden abierta hacia cada zona deficitaria.
CREATE UNIQUE INDEX ux_relocation_open_target ON relocation_order (to_zone_id)
    WHERE status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS');
