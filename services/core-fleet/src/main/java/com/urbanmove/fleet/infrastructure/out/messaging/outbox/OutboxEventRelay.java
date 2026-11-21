package com.urbanmove.fleet.infrastructure.out.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.fleet.domain.event.EventEnvelope;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.EventPublicationJpaEntity;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.OutboxEventJpaEntity;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataEventPublicationRepository;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Relay del Outbox transaccional (ADR 0007).
 * Reclama eventos pendientes con SKIP LOCKED, envía a SQS FUERA de la transacción principal
 * y registra cada intento en la tabla inmutable event_publications.
 */
@Component
public class OutboxEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRelay.class);
    private static final int MAX_ATTEMPTS = 5;

    private final SpringDataOutboxRepository outboxRepository;
    private final SpringDataEventPublicationRepository publicationRepository;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    public OutboxEventRelay(
            SpringDataOutboxRepository outboxRepository,
            SpringDataEventPublicationRepository publicationRepository,
            SqsClient sqsClient,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.publicationRepository = Objects.requireNonNull(publicationRepository);
        this.sqsClient = Objects.requireNonNull(sqsClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Scheduled(fixedDelayString = "${fleet.outbox.relay-interval:2000}")
    public int relayPendingEvents() {
        // 1. Reclamar lote de eventos pendientes usando SKIP LOCKED en una transacción corta
        List<OutboxEventJpaEntity> batch = claimBatch();
        if (batch.isEmpty()) {
            return 0;
        }

        int publishedCount = 0;
        // 2. Procesar cada evento: el envío de red se hace FUERA de la transacción de claim
        for (OutboxEventJpaEntity event : batch) {
            boolean success = dispatchEvent(event);
            if (success) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    @Transactional
    public List<OutboxEventJpaEntity> claimBatch() {
        return outboxRepository.findPendingEventsForClaim(20);
    }

    private boolean dispatchEvent(OutboxEventJpaEntity event) {
        int currentAttempt = publicationRepository.findMaxAttemptNumberByEventId(event.getId()).orElse(0) + 1;
        Instant now = Instant.now();

        try {
            // Reconstruir sobre canónico para enviarlo exactamente según EVENTS.md
            Object payloadNode = objectMapper.readTree(event.getPayload());
            EventEnvelope<Object> envelope = new EventEnvelope<>(
                    event.getId(),
                    event.getEventType(),
                    event.getEventVersion(),
                    event.getSchemaUri(),
                    event.getOccurredAt(),
                    event.getRecordedAt(),
                    event.getSource(),
                    event.getProducer(),
                    event.getCorrelationId(),
                    event.getCausationId(),
                    payloadNode
            );

            String messageBody = objectMapper.writeValueAsString(envelope);

            // I/O de red con SQS
            SendMessageRequest sendRequest = SendMessageRequest.builder()
                    .queueUrl(event.getDestination())
                    .messageBody(messageBody)
                    .build();
            sqsClient.sendMessage(sendRequest);

            // Registrar publicación exitosa inmutable
            recordPublication(event.getId(), event.getDestination(), currentAttempt, "SUCCESS", null, now);
            log.info("Relayed outbox event [{}] of type [{}] to destination [{}] (attempt {})",
                    event.getId(), event.getEventType(), event.getDestination(), currentAttempt);
            return true;

        } catch (Exception ex) {
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            String status = currentAttempt >= MAX_ATTEMPTS ? "DEAD_LETTER" : "FAILED";

            recordPublication(event.getId(), event.getDestination(), currentAttempt, status, errorMsg, now);

            if ("DEAD_LETTER".equals(status)) {
                log.error("Outbox event [{}] moved to DEAD_LETTER after {} failed attempts: {}",
                        event.getId(), currentAttempt, errorMsg);
            } else {
                log.warn("Failed to relay outbox event [{}] (attempt {}/{}): {}",
                        event.getId(), currentAttempt, MAX_ATTEMPTS, errorMsg);
            }
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPublication(
            UUID eventId,
            String destination,
            int attemptNumber,
            String status,
            String errorMessage,
            Instant publishedAt
    ) {
        EventPublicationJpaEntity publication = new EventPublicationJpaEntity(
                UUID.randomUUID(),
                eventId,
                destination,
                attemptNumber,
                status,
                errorMessage,
                publishedAt
        );
        publicationRepository.save(publication);
    }
}
