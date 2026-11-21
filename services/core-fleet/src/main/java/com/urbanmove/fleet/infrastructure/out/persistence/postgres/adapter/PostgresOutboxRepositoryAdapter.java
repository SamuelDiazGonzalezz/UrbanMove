package com.urbanmove.fleet.infrastructure.out.persistence.postgres.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.fleet.domain.event.EventEnvelope;
import com.urbanmove.fleet.domain.port.out.OutboxRepositoryPort;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.OutboxEventJpaEntity;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataOutboxRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PostgresOutboxRepositoryAdapter implements OutboxRepositoryPort {

    private final SpringDataOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public PostgresOutboxRepositoryAdapter(SpringDataOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = Objects.requireNonNull(repository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void save(EventEnvelope<?> envelope, String destination) {
        try {
            String payloadJson = objectMapper.writeValueAsString(envelope.payload());
            OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.eventVersion(),
                    envelope.schema(),
                    envelope.occurredAt(),
                    envelope.recordedAt(),
                    envelope.source(),
                    envelope.producer(),
                    envelope.correlationId(),
                    envelope.causationId(),
                    destination,
                    payloadJson,
                    envelope.recordedAt()
            );
            repository.save(entity);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize event payload to JSON", e);
        }
    }
}
