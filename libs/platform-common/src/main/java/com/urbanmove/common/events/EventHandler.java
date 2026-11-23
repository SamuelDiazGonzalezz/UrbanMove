package com.urbanmove.common.events;

import java.util.Set;

/**
 * Manejador de eventos consumidos del bus. Se ejecuta dentro de la transacción que registra
 * la idempotencia en {@code processed_event}: si lanza una excepción, el mensaje se reintenta
 * y, tras varios intentos, SQS lo mueve a la DLQ.
 */
public interface EventHandler {

    Set<String> eventTypes();

    void handle(EventEnvelope event);
}
