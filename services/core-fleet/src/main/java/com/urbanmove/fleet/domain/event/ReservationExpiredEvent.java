package com.urbanmove.fleet.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationExpiredEvent(
        UUID eventId,
        UUID reservationId,
        UUID vehicleId,
        String userId,
        Instant occurredAt
) {
    public static ReservationExpiredEvent of(UUID reservationId, UUID vehicleId, String userId, Instant occurredAt) {
        return new ReservationExpiredEvent(UUID.randomUUID(), reservationId, vehicleId, userId, occurredAt);
    }
}
