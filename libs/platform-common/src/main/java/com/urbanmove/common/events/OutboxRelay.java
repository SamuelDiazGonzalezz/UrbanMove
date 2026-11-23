package com.urbanmove.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Relay del outbox: publica en EventBridge los eventos del {@code event_store} que aún no tienen
 * fila en {@code event_publication}. No hay UPDATE: la fila de publicación es la prueba.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 50;
    private static final int PUT_EVENTS_MAX = 10;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final EventBridgeClient eventBridge;
    private final ObjectMapper objectMapper;
    private final String busName;
    private final boolean enabled;
    private long lastWarnAt = 0;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, EventBridgeClient eventBridge, ObjectMapper objectMapper,
                       @Value("${urbanmove.events.bus-name:urbanmove-bus}") String busName,
                       @Value("${urbanmove.events.publish-enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.eventBridge = eventBridge;
        this.objectMapper = objectMapper;
        this.busName = busName;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${urbanmove.events.relay-delay-ms:500}")
    public void relay() {
        if (!enabled) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> publishBatch());
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastWarnAt > 30_000) {
                log.warn("Outbox relay sin publicar (se reintenta): {}", e.getMessage());
                lastWarnAt = now;
            }
        }
    }

    private void publishBatch() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.event_id, e.event_type, e.event_version, e.schema_uri, e.occurred_at, e.recorded_at,
                       e.source, e.producer, e.correlation_id, e.causation_id, e.payload::text AS payload
                FROM event_store e
                WHERE NOT EXISTS (SELECT 1 FROM event_publication p WHERE p.event_id = e.event_id)
                ORDER BY e.global_position
                LIMIT ?
                FOR UPDATE OF e SKIP LOCKED
                """, BATCH);
        for (int i = 0; i < rows.size(); i += PUT_EVENTS_MAX) {
            List<Map<String, Object>> chunk = rows.subList(i, Math.min(rows.size(), i + PUT_EVENTS_MAX));
            List<PutEventsRequestEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : chunk) {
                entries.add(PutEventsRequestEntry.builder()
                        .eventBusName(busName)
                        .source("urbanmove." + row.get("producer"))
                        .detailType((String) row.get("event_type"))
                        .detail(toEnvelopeJson(row))
                        .build());
            }
            PutEventsResponse response = eventBridge.putEvents(PutEventsRequest.builder().entries(entries).build());
            for (int j = 0; j < chunk.size(); j++) {
                if (response.entries().get(j).errorCode() == null) {
                    jdbc.update("INSERT INTO event_publication (event_id) VALUES (?) ON CONFLICT DO NOTHING",
                            (UUID) chunk.get(j).get("event_id"));
                } else {
                    log.warn("EventBridge rechazó {}: {}", chunk.get(j).get("event_id"), response.entries().get(j).errorMessage());
                }
            }
        }
    }

    private String toEnvelopeJson(Map<String, Object> row) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", row.get("event_id").toString());
            envelope.put("eventType", row.get("event_type"));
            envelope.put("eventVersion", row.get("event_version"));
            envelope.put("schema", row.get("schema_uri"));
            envelope.put("occurredAt", ((java.sql.Timestamp) row.get("occurred_at")).toInstant().toString());
            envelope.put("recordedAt", ((java.sql.Timestamp) row.get("recorded_at")).toInstant().toString());
            envelope.put("source", row.get("source"));
            envelope.put("producer", row.get("producer"));
            envelope.put("correlationId", row.get("correlation_id"));
            envelope.put("causationId", row.get("causation_id"));
            envelope.put("payload", objectMapper.readTree((String) row.get("payload")));
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo construir el sobre del evento " + row.get("event_id"), e);
        }
    }
}
