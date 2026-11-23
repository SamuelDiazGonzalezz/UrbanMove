-- Historial unificado de todos los eventos de negocio (consumidor del bus).
CREATE TABLE history_entry (
    event_id       UUID PRIMARY KEY,
    event_type     TEXT        NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,
    producer       TEXT        NOT NULL,
    correlation_id TEXT,
    user_id        TEXT,
    vehicle_id     TEXT,
    zone_id        TEXT,
    payload        JSONB       NOT NULL,
    received_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_history_user ON history_entry (user_id, occurred_at DESC);
CREATE INDEX idx_history_vehicle ON history_entry (vehicle_id, occurred_at DESC);
CREATE INDEX idx_history_correlation ON history_entry (correlation_id);
CREATE INDEX idx_history_time ON history_entry (occurred_at DESC);
