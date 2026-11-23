-- analytics_db (OLAP). La escribe solo la Lambda mart-builder; este servicio solo lee.

-- Capa trusted cargada en PostgreSQL: un evento validado por fila.
CREATE TABLE fact_event (
    event_id       UUID PRIMARY KEY,
    event_type     TEXT        NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,
    producer       TEXT        NOT NULL,
    correlation_id TEXT,
    user_id        TEXT,
    vehicle_id     TEXT,
    zone_id        TEXT,
    payload        JSONB       NOT NULL,
    ingested_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fact_type_time ON fact_event (event_type, occurred_at DESC);

-- Cada ejecución del builder añade un build; las vistas *_current leen el último.
CREATE TABLE mart_build (
    build_id     UUID PRIMARY KEY,
    built_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    facts_count  BIGINT      NOT NULL,
    duration_ms  INT         NOT NULL,
    published    BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE TABLE mart_flota (
    build_id     UUID   NOT NULL REFERENCES mart_build (build_id) ON DELETE CASCADE,
    zone_id      TEXT,
    vehicle_type TEXT,
    state        TEXT   NOT NULL,
    vehicles     BIGINT NOT NULL
);

CREATE TABLE mart_reservas (
    build_id  UUID     NOT NULL REFERENCES mart_build (build_id) ON DELETE CASCADE,
    date_key  DATE     NOT NULL,
    hour      SMALLINT NOT NULL,
    zone_id   TEXT,
    reserved  BIGINT   NOT NULL,
    cancelled BIGINT   NOT NULL,
    expired   BIGINT   NOT NULL,
    completed BIGINT   NOT NULL,
    rejected  BIGINT   NOT NULL
);

CREATE TABLE mart_viajes (
    build_id              UUID     NOT NULL REFERENCES mart_build (build_id) ON DELETE CASCADE,
    date_key              DATE     NOT NULL,
    hour                  SMALLINT NOT NULL,
    origin_zone_id        TEXT,
    destination_zone_id   TEXT,
    vehicle_type          TEXT,
    trips                 BIGINT   NOT NULL,
    avg_duration_seconds  NUMERIC(10, 1),
    total_distance_meters BIGINT,
    revenue               NUMERIC(12, 2)
);

CREATE TABLE mart_demanda (
    build_id                UUID   NOT NULL REFERENCES mart_build (build_id) ON DELETE CASCADE,
    zone_id                 TEXT   NOT NULL,
    zone_name               TEXT,
    target_vehicles         INT,
    available_now           BIGINT NOT NULL,
    reservations_last_hour  BIGINT NOT NULL,
    rejections_last_hour    BIGINT NOT NULL,
    rides_started_last_hour BIGINT NOT NULL,
    demand_last_hour        BIGINT NOT NULL,
    balance                 BIGINT NOT NULL,
    status                  TEXT   NOT NULL
);

CREATE TABLE mart_mantenimiento (
    build_id          UUID   NOT NULL REFERENCES mart_build (build_id) ON DELETE CASCADE,
    date_key          DATE   NOT NULL,
    battery_low       BIGINT NOT NULL,
    incidents         BIGINT NOT NULL,
    orders_opened     BIGINT NOT NULL,
    orders_completed  BIGINT NOT NULL,
    returned          BIGINT NOT NULL,
    avg_minutes_out   NUMERIC(10, 1)
);

CREATE TABLE mart_operaciones (
    build_id                UUID   NOT NULL REFERENCES mart_build (build_id) ON DELETE CASCADE,
    date_key                DATE   NOT NULL,
    to_zone_id              TEXT,
    orders_created          BIGINT NOT NULL,
    orders_completed        BIGINT NOT NULL,
    vehicles_moved          BIGINT NOT NULL,
    avg_minutes_to_complete NUMERIC(10, 1)
);

CREATE TABLE mart_usuarios (
    build_id      UUID   NOT NULL REFERENCES mart_build (build_id) ON DELETE CASCADE,
    date_key      DATE   NOT NULL,
    active_users  BIGINT NOT NULL,
    new_users     BIGINT NOT NULL,
    trips         BIGINT NOT NULL,
    cancellations BIGINT NOT NULL
);

-- Enfriamiento de la regla de recolocación: evita generar la misma señal en bucle.
CREATE TABLE demand_signal (
    zone_id          TEXT PRIMARY KEY,
    last_detected_at TIMESTAMPTZ NOT NULL
);

CREATE VIEW mart_build_current AS
SELECT * FROM mart_build ORDER BY built_at DESC LIMIT 1;

CREATE VIEW mart_flota_current AS SELECT * FROM mart_flota WHERE build_id = (SELECT build_id FROM mart_build_current);
CREATE VIEW mart_reservas_current AS SELECT * FROM mart_reservas WHERE build_id = (SELECT build_id FROM mart_build_current);
CREATE VIEW mart_viajes_current AS SELECT * FROM mart_viajes WHERE build_id = (SELECT build_id FROM mart_build_current);
CREATE VIEW mart_demanda_current AS SELECT * FROM mart_demanda WHERE build_id = (SELECT build_id FROM mart_build_current);
CREATE VIEW mart_mantenimiento_current AS SELECT * FROM mart_mantenimiento WHERE build_id = (SELECT build_id FROM mart_build_current);
CREATE VIEW mart_operaciones_current AS SELECT * FROM mart_operaciones WHERE build_id = (SELECT build_id FROM mart_build_current);
CREATE VIEW mart_usuarios_current AS SELECT * FROM mart_usuarios WHERE build_id = (SELECT build_id FROM mart_build_current);
