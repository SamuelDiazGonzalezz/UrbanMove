package com.urbanmove.fleet.domain.port.out;

import com.urbanmove.fleet.domain.event.EventEnvelope;

/**
 * Puerto de salida para persistir eventos de dominio en el Outbox transaccional.
 */
public interface OutboxRepositoryPort {
    void save(EventEnvelope<?> envelope, String destination);
}
