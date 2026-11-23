package com.urbanmove.common.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Sobre canónico de evento (ver docs/01-arquitectura/EVENTS.md, sección 1).
 * El sobre es estable; {@code eventVersion} versiona solo el payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
        String eventId,
        String eventType,
        int eventVersion,
        String schema,
        Instant occurredAt,
        Instant recordedAt,
        String source,
        String producer,
        String correlationId,
        String causationId,
        JsonNode payload
) {

    public String text(String field) {
        JsonNode node = payload == null ? null : payload.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    public Integer integer(String field) {
        JsonNode node = payload == null ? null : payload.get(field);
        return node == null || node.isNull() ? null : node.asInt();
    }

    public Double decimal(String field) {
        JsonNode node = payload == null ? null : payload.get(field);
        return node == null || node.isNull() ? null : node.asDouble();
    }
}
