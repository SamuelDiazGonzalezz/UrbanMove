package com.urbanmove.fleet.infrastructure.out.messaging.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.fleet.domain.event.ReservationCreatedEvent;
import com.urbanmove.fleet.domain.event.TripRecordedEvent;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
public class LocalstackSqsEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(LocalstackSqsEventPublisherAdapter.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public LocalstackSqsEventPublisherAdapter(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            @Value("${cloud.aws.sqs.queue-url:http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/trip-recorded-queue}") String queueUrl
    ) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publish(ReservationCreatedEvent event) {
        sendToQueue(event, "ReservationCreatedEvent");
    }

    @Override
    public void publish(TripRecordedEvent event) {
        sendToQueue(event, "TripRecordedEvent");
    }

    private void sendToQueue(Object event, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(payload)
                    .build();
            sqsClient.sendMessage(request);
            log.info("Published domain event [{}] to SQS queue {}: {}", eventType, queueUrl, payload);
        } catch (Exception e) {
            log.error("Failed to publish {} to SQS: {}", eventType, e.getMessage(), e);
        }
    }
}
