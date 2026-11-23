package com.urbanmove.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.common.web.ApiException;
import com.urbanmove.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event store append-only del servicio. Guardar un evento aquí es, a la vez, encolarlo para
 * publicación: el {@link OutboxRelay} publica toda fila sin registro en {@code event_publication}.
 * Debe llamarse dentro de la misma transacción que el cambio de estado.
 */
@Component
public class EventStore {

    private static final String SCHEMA_BASE = "https://urbanmove.local/schemas/";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final String producer;

    public EventStore(JdbcTemplate jdbc, ObjectMapper objectMapper,
                      @Value("${spring.application.name}") String producer) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.producer = producer;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String append(String aggregateType, String aggregateId, String eventType, Map<String, ?> payload) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(aggregate_version), 0) + 1 FROM event_store WHERE aggregate_type = ? AND aggregate_id = ?",
                Integer.class, aggregateType, aggregateId);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        try {
            jdbc.update("""
                    INSERT INTO event_store (event_id, aggregate_type, aggregate_id, aggregate_version, event_type,
                        event_version, schema_uri, occurred_at, recorded_at, source, producer, correlation_id, causation_id, payload)
                    VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """,
                    eventId, aggregateType, aggregateId, next, eventType,
                    SCHEMA_BASE + eventType + ".v1.json", Timestamp.from(now), Timestamp.from(now),
                    aggregateType + "-" + aggregateId, producer, correlationId,
                    MDC.get(CorrelationIdFilter.MDC_CAUSATION), objectMapper.writeValueAsString(payload));
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                    "Otro cambio sobre " + aggregateType + " " + aggregateId + " llegó antes. Reintenta.");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload no serializable para " + eventType, e);
        }
        return eventId.toString();
    }
}
