package com.urbanmove.fleet.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Reservation(
        UUID id,
        UUID vehicleId,
        String userId,
        Instant reservedAt,
        ReservationStatus status
) {
    public enum ReservationStatus {
        ACTIVE,
        CANCELLED,
        COMPLETED
    }

    public Reservation {
        Objects.requireNonNull(id, "Reservation id cannot be null");
        Objects.requireNonNull(vehicleId, "Vehicle id cannot be null");
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(reservedAt, "Reserved at cannot be null");
        Objects.requireNonNull(status, "Reservation status cannot be null");
    }

    public static Reservation createNew(UUID vehicleId, String userId) {
        return new Reservation(UUID.randomUUID(), vehicleId, userId, Instant.now(), ReservationStatus.ACTIVE);
    }
}
