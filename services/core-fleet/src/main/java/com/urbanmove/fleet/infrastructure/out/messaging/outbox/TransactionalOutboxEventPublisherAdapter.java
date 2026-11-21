package com.urbanmove.fleet.infrastructure.out.messaging.outbox;

import com.urbanmove.fleet.domain.event.EventEnvelope;
import com.urbanmove.fleet.domain.event.ReservationCancelledEvent;
import com.urbanmove.fleet.domain.event.ReservationCreatedEvent;
import com.urbanmove.fleet.domain.event.ReservationExpiredEvent;
import com.urbanmove.fleet.domain.event.TripRecordedEvent;
import com.urbanmove.fleet.domain.event.payload.ReservationCancelledPayload;
import com.urbanmove.fleet.domain.event.payload.ReservationExpiredPayload;
import com.urbanmove.fleet.domain.event.payload.TripRecordedPayload;
import com.urbanmove.fleet.domain.event.payload.VehicleReservedPayload;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.domain.port.out.OutboxRepositoryPort;
import com.urbanmove.fleet.infrastructure.in.web.filter.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * Implementación de EventPublisherPort que persiste los eventos en el Outbox
 * transaccional dentro de la misma transacción de base de datos de negocio.
 */
@Component
@Primary
public class TransactionalOutboxEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(TransactionalOutboxEventPublisherAdapter.class);

    private final OutboxRepositoryPort outboxRepository;
    private final String defaultQueueUrl;

    public TransactionalOutboxEventPublisherAdapter(
            OutboxRepositoryPort outboxRepository,
            @Value("${cloud.aws.sqs.queue-url:http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/trip-recorded-queue}") String defaultQueueUrl
    ) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.defaultQueueUrl = defaultQueueUrl;
    }

    @Override
    public void publish(ReservationCreatedEvent event) {
        VehicleReservedPayload payload = new VehicleReservedPayload(
                event.reservationId(),
                event.vehicleId(),
                event.vehicleCode(),
                event.userId(),
                event.occurredAt().plusSeconds(900) // 15 min
        );
        EventEnvelope<VehicleReservedPayload> envelope = EventEnvelope.create(
                "VehicleReserved",
                "https://urbanmove.local/schemas/VehicleReserved.v1.json",
                event.occurredAt(),
                "vehicle-" + event.vehicleCode(),
                "reservations-service",
                CorrelationIdFilter.getCorrelationId(),
                null,
                payload
        );
        outboxRepository.save(envelope, defaultQueueUrl);
        log.info("Enqueued event [VehicleReserved] to transactional outbox for reservation [{}]", event.reservationId());
    }

    @Override
    public void publish(ReservationCancelledEvent event) {
        ReservationCancelledPayload payload = new ReservationCancelledPayload(
                event.reservationId(),
                event.vehicleId(),
                event.userId()
        );
        EventEnvelope<ReservationCancelledPayload> envelope = EventEnvelope.create(
                "ReservationCancelled",
                "https://urbanmove.local/schemas/ReservationCancelled.v1.json",
                event.occurredAt(),
                "reservations-service",
                "reservations-service",
                CorrelationIdFilter.getCorrelationId(),
                null,
                payload
        );
        outboxRepository.save(envelope, defaultQueueUrl);
        log.info("Enqueued event [ReservationCancelled] to transactional outbox for reservation [{}]", event.reservationId());
    }

    @Override
    public void publish(ReservationExpiredEvent event) {
        ReservationExpiredPayload payload = new ReservationExpiredPayload(
                event.reservationId(),
                event.vehicleId(),
                event.userId()
        );
        EventEnvelope<ReservationExpiredPayload> envelope = EventEnvelope.create(
                "ReservationExpired",
                "https://urbanmove.local/schemas/ReservationExpired.v1.json",
                event.occurredAt(),
                "reservations-service",
                "reservations-service",
                CorrelationIdFilter.getCorrelationId(),
                null,
                payload
        );
        outboxRepository.save(envelope, defaultQueueUrl);
        log.info("Enqueued event [ReservationExpired] to transactional outbox for reservation [{}]", event.reservationId());
    }

    @Override
    public void publish(TripRecordedEvent event) {
        TripRecordedPayload payload = new TripRecordedPayload(
                event.rideId(),
                event.reservationId(),
                event.vehicleId(),
                event.vehicleCode(),
                event.userId(),
                event.startedAt(),
                event.finishedAt(),
                event.distanceKm(),
                event.costAmount(),
                event.endLatitude(),
                event.endLongitude()
        );
        EventEnvelope<TripRecordedPayload> envelope = EventEnvelope.create(
                "TripRecorded",
                "https://urbanmove.local/schemas/TripRecorded.v1.json",
                event.finishedAt() != null ? event.finishedAt() : Instant.now(),
                "vehicle-" + event.vehicleCode(),
                "rides-service",
                CorrelationIdFilter.getCorrelationId(),
                event.reservationId() != null ? event.reservationId().toString() : null,
                payload
        );
        outboxRepository.save(envelope, defaultQueueUrl);
        log.info("Enqueued event [TripRecorded] to transactional outbox for ride [{}]", event.rideId());
    }
}
