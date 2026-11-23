package com.urbanmove.common.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.common.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consume la cola SQS propia del servicio (alimentada por reglas de EventBridge), registra la
 * idempotencia por {@code eventId} y despacha a los {@link EventHandler}.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String queueName;
    private final Map<String, List<EventHandler>> handlers = new HashMap<>();
    private String queueUrl;
    private long lastWarnAt = 0;

    public EventConsumer(SqsClient sqs, ObjectMapper objectMapper, JdbcTemplate jdbc, TransactionTemplate tx,
                         List<EventHandler> handlerBeans,
                         @Value("${urbanmove.events.queue-name:}") String queueName) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.tx = tx;
        this.queueName = queueName;
        for (EventHandler handler : handlerBeans) {
            for (String type : handler.eventTypes()) {
                handlers.computeIfAbsent(type, k -> new java.util.ArrayList<>()).add(handler);
            }
        }
    }

    @Scheduled(fixedDelayString = "${urbanmove.events.poll-delay-ms:100}")
    public void poll() {
        if (queueName.isBlank() || handlers.isEmpty()) {
            return;
        }
        try {
            if (queueUrl == null) {
                queueUrl = sqs.getQueueUrl(b -> b.queueName(queueName)).queueUrl();
                log.info("Consumiendo eventos de {}", queueUrl);
            }
            List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(2).build()).messages();
            for (Message message : messages) {
                if (process(message)) {
                    sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
                }
            }
        } catch (Exception e) {
            queueUrl = null;
            long now = System.currentTimeMillis();
            if (now - lastWarnAt > 30_000) {
                log.warn("Cola {} no disponible todavía: {}", queueName, e.getMessage());
                lastWarnAt = now;
            }
        }
    }

    private boolean process(Message message) {
        EventEnvelope event;
        try {
            JsonNode root = objectMapper.readTree(message.body());
            JsonNode envelope = root.has("detail") ? root.get("detail") : root;
            if (envelope.isTextual()) {
                envelope = objectMapper.readTree(envelope.asText());
            }
            event = objectMapper.treeToValue(envelope, EventEnvelope.class);
        } catch (Exception e) {
            log.error("Mensaje ilegible descartado a la DLQ tras reintentos: {}", e.getMessage());
            return false;
        }
        List<EventHandler> targets = new java.util.ArrayList<>(handlers.getOrDefault(event.eventType(), List.of()));
        targets.addAll(handlers.getOrDefault("*", List.of()));
        if (targets.isEmpty()) {
            return true;
        }
        MDC.put(CorrelationIdFilter.MDC_CORRELATION, event.correlationId());
        MDC.put(CorrelationIdFilter.MDC_CAUSATION, event.eventId());
        try {
            tx.executeWithoutResult(status -> {
                int inserted = jdbc.update(
                        "INSERT INTO processed_event (event_id, event_type) VALUES (?, ?) ON CONFLICT DO NOTHING",
                        UUID.fromString(event.eventId()), event.eventType());
                if (inserted == 0) {
                    return;
                }
                for (EventHandler handler : targets) {
                    handler.handle(event);
                }
            });
            return true;
        } catch (Exception e) {
            log.error("Fallo procesando {} {} (se reintentará): {}", event.eventType(), event.eventId(), e.getMessage(), e);
            return false;
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_CORRELATION);
            MDC.remove(CorrelationIdFilter.MDC_CAUSATION);
        }
    }
}
