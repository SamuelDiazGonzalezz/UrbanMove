package com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "schema_uri", nullable = false)
    private String schemaUri;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "producer", nullable = false, length = 100)
    private String producer;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "causation_id", length = 100)
    private String causationId;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxEventJpaEntity() {}

    public OutboxEventJpaEntity(
            UUID id,
            String eventType,
            int eventVersion,
            String schemaUri,
            Instant occurredAt,
            Instant recordedAt,
            String source,
            String producer,
            String correlationId,
            String causationId,
            String destination,
            String payload,
            Instant createdAt
    ) {
        this.id = id;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.schemaUri = schemaUri;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.source = source;
        this.producer = producer;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.destination = destination;
        this.payload = payload;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public String getSchemaUri() { return schemaUri; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getSource() { return source; }
    public String getProducer() { return producer; }
    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }
    public String getDestination() { return destination; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
