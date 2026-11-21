-- ====================================================================
-- V3: Transactional Outbox & Event Publication History (ADR 0007)
-- ====================================================================

-- 1. Tabla de eventos Outbox (Append-Only)
-- Contiene el sobre completo normalizado según EVENTS.md y el payload JSON
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    schema_uri VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(100) NOT NULL,
    producer VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    causation_id VARCHAR(100),
    destination VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índice por fecha de registro para escaneo ordenado
CREATE INDEX IF NOT EXISTS idx_outbox_events_created_at 
ON outbox_events (created_at ASC);

-- 2. Tabla de publicaciones de eventos (Append-Only, sin UPDATE destructivo)
-- Registra cada intento de entrega (SUCCESS, FAILED, DEAD_LETTER)
CREATE TABLE IF NOT EXISTS event_publications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES outbox_events(id) ON DELETE CASCADE,
    destination VARCHAR(255) NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL, -- 'SUCCESS', 'FAILED', 'DEAD_LETTER'
    error_message TEXT,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_publication_status CHECK (status IN ('SUCCESS', 'FAILED', 'DEAD_LETTER'))
);

CREATE INDEX IF NOT EXISTS idx_event_publications_event_id 
ON event_publications (event_id, attempt_number DESC);

-- 3. Vista para identificar eventos pendientes o en reintento
-- Un evento está pendiente si no tiene publicación SUCCESS ni DEAD_LETTER
CREATE OR REPLACE VIEW view_pending_outbox_events AS
SELECT o.*,
       COALESCE(MAX(p.attempt_number), 0) AS total_attempts,
       MAX(p.published_at) AS last_attempt_at
FROM outbox_events o
LEFT JOIN event_publications p ON o.id = p.event_id
GROUP BY o.id
HAVING bool_or(p.status = 'SUCCESS') IS NOT TRUE 
   AND bool_or(p.status = 'DEAD_LETTER') IS NOT TRUE;
