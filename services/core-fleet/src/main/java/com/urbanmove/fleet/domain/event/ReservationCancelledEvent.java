package com.urbanmove.fleet.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationCancelledEvent(
        UUID eventId,
        UUID reservationId,
        UUID vehicleId,
        String userId,
        Instant occurredAt
) {
    public static ReservationCancelledEvent of(UUID reservationId, UUID vehicleId, String userId, Instant occurredAt) {
        return new ReservationCancelledEvent(UUID.randomUUID(), reservationId, vehicleId, userId, occurredAt);
    }
}
