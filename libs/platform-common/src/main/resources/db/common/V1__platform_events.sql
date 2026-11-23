-- Tablas comunes a todos los servicios (EVENTS.md §4, EVENT-SOURCING.md §2 y §6).
-- event_store: verdad append-only del servicio. Toda fila es también la intención de publicar.
CREATE TABLE IF NOT EXISTS event_store (
    global_position   BIGSERIAL PRIMARY KEY,
    event_id          UUID        NOT NULL UNIQUE,
    aggregate_type    TEXT        NOT NULL,
    aggregate_id      TEXT        NOT NULL,
    aggregate_version INT         NOT NULL,
    event_type        TEXT        NOT NULL,
    event_version     INT         NOT NULL DEFAULT 1,
    schema_uri        TEXT        NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    source            TEXT        NOT NULL,
    producer          TEXT        NOT NULL,
    correlation_id    TEXT        NOT NULL,
    causation_id      TEXT,
    payload           JSONB       NOT NULL,
    UNIQUE (aggregate_type, aggregate_id, aggregate_version)
);

-- Registro inmutable de publicaciones: la existencia de la fila es la prueba de publicación.
CREATE TABLE IF NOT EXISTS event_publication (
    event_id     UUID PRIMARY KEY REFERENCES event_store (event_id),
    published_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotencia del consumidor.
CREATE TABLE IF NOT EXISTS processed_event (
    event_id     UUID PRIMARY KEY,
    event_type   TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
