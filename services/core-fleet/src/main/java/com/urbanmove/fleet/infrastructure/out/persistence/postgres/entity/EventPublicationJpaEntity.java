package com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_publications")
public class EventPublicationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // 'SUCCESS', 'FAILED', 'DEAD_LETTER'

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected EventPublicationJpaEntity() {}

    public EventPublicationJpaEntity(
            UUID id,
            UUID eventId,
            String destination,
            int attemptNumber,
            String status,
            String errorMessage,
            Instant publishedAt
    ) {
        this.id = id;
        this.eventId = eventId;
        this.destination = destination;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.errorMessage = errorMessage;
        this.publishedAt = publishedAt != null ? publishedAt : Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getDestination() { return destination; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getPublishedAt() { return publishedAt; }
}
