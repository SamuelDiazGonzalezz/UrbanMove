package com.urbanmove.fleet.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre de evento canónico según EVENTS.md y ADR 0007.
 * Todos los eventos publicados por el dominio usan esta estructura estándar.
 *
 * @param <T> Tipo del payload específico del evento.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        String schema,
        Instant occurredAt,
        Instant recordedAt,
        String source,
        String producer,
        String correlationId,
        String causationId,
        T payload
) {
    public static <T> EventEnvelope<T> create(
            String eventType,
            String schema,
            Instant occurredAt,
            String source,
            String producer,
            String correlationId,
            String causationId,
            T payload
    ) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                schema,
                occurredAt,
                Instant.now(),
                source,
                producer,
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                causationId,
                payload
        );
    }
}
