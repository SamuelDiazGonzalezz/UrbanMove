package com.urbanmove.fleet.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreatedEvent(
        UUID eventId,
        UUID reservationId,
        UUID vehicleId,
        String vehicleCode,
        String userId,
        Instant occurredAt
) {
    public static ReservationCreatedEvent of(UUID reservationId, UUID vehicleId, String vehicleCode, String userId) {
        return new ReservationCreatedEvent(UUID.randomUUID(), reservationId, vehicleId, vehicleCode, userId, Instant.now());
    }
}
