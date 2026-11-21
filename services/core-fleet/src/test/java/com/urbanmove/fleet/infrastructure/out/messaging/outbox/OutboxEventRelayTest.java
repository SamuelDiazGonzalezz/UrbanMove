package com.urbanmove.fleet.infrastructure.out.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.EventPublicationJpaEntity;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.OutboxEventJpaEntity;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataEventPublicationRepository;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventRelayTest {

    @Mock
    private SpringDataOutboxRepository outboxRepository;

    @Mock
    private SpringDataEventPublicationRepository publicationRepository;

    @Mock
    private SqsClient sqsClient;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private OutboxEventRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxEventRelay(outboxRepository, publicationRepository, sqsClient, objectMapper);
    }

    @Test
    @DisplayName("Debe publicar evento a SQS con éxito y registrar publicación inmutable SUCCESS")
    void shouldRelayPendingEventSuccessfully() {
        UUID eventId = UUID.randomUUID();
        OutboxEventJpaEntity event = new OutboxEventJpaEntity(
                eventId,
                "VehicleReserved",
                1,
                "https://urbanmove.local/schemas/VehicleReserved.v1.json",
                Instant.now(),
                Instant.now(),
                "vehicle-SCOOT-1",
                "reservations-service",
                "corr-123",
                null,
                "http://sqs.mock/queue",
                "{\"vehicleCode\":\"SCOOT-1\"}",
                Instant.now()
        );

        when(outboxRepository.findPendingEventsForClaim(20)).thenReturn(List.of(event));
        when(publicationRepository.findMaxAttemptNumberByEventId(eventId)).thenReturn(Optional.empty());
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        int relayed = relay.relayPendingEvents();

        assertThat(relayed).isEqualTo(1);
        verify(sqsClient).sendMessage(any(SendMessageRequest.class));

        ArgumentCaptor<EventPublicationJpaEntity> pubCaptor = ArgumentCaptor.forClass(EventPublicationJpaEntity.class);
        verify(publicationRepository).save(pubCaptor.capture());
        assertThat(pubCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(pubCaptor.getValue().getAttemptNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Debe registrar FAILED en event_publications cuando falla SQS")
    void shouldRecordFailedWhenSqsFails() {
        UUID eventId = UUID.randomUUID();
        OutboxEventJpaEntity event = new OutboxEventJpaEntity(
                eventId,
                "TripRecorded",
                1,
                "https://urbanmove.local/schemas/TripRecorded.v1.json",
                Instant.now(),
                Instant.now(),
                "vehicle-SCOOT-1",
                "rides-service",
                "corr-123",
                null,
                "http://sqs.mock/queue",
                "{\"cost\":1.5}",
                Instant.now()
        );

        when(outboxRepository.findPendingEventsForClaim(20)).thenReturn(List.of(event));
        when(publicationRepository.findMaxAttemptNumberByEventId(eventId)).thenReturn(Optional.of(1));
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(new RuntimeException("SQS connection timeout"));

        int relayed = relay.relayPendingEvents();

        assertThat(relayed).isEqualTo(0);
        ArgumentCaptor<EventPublicationJpaEntity> pubCaptor = ArgumentCaptor.forClass(EventPublicationJpaEntity.class);
        verify(publicationRepository).save(pubCaptor.capture());
        assertThat(pubCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(pubCaptor.getValue().getAttemptNumber()).isEqualTo(2);
        assertThat(pubCaptor.getValue().getErrorMessage()).contains("SQS connection timeout");
    }

    @Test
    @DisplayName("Debe marcar como DEAD_LETTER al alcanzar el quinto intento fallido")
    void shouldMarkDeadLetterAfterMaxAttempts() {
        UUID eventId = UUID.randomUUID();
        OutboxEventJpaEntity event = new OutboxEventJpaEntity(
                eventId,
                "TripRecorded",
                1,
                "https://urbanmove.local/schemas/TripRecorded.v1.json",
                Instant.now(),
                Instant.now(),
                "vehicle-SCOOT-1",
                "rides-service",
                "corr-123",
                null,
                "http://sqs.mock/queue",
                "{\"cost\":1.5}",
                Instant.now()
        );

        when(outboxRepository.findPendingEventsForClaim(20)).thenReturn(List.of(event));
        when(publicationRepository.findMaxAttemptNumberByEventId(eventId)).thenReturn(Optional.of(4)); // Siguiente intento es 5
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(new RuntimeException("Unrecoverable error"));

        int relayed = relay.relayPendingEvents();

        assertThat(relayed).isEqualTo(0);
        ArgumentCaptor<EventPublicationJpaEntity> pubCaptor = ArgumentCaptor.forClass(EventPublicationJpaEntity.class);
        verify(publicationRepository).save(pubCaptor.capture());
        assertThat(pubCaptor.getValue().getStatus()).isEqualTo("DEAD_LETTER");
        assertThat(pubCaptor.getValue().getAttemptNumber()).isEqualTo(5);
    }
}
