package com.urbanmove.fleet.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record Reservation(
        UUID id,
        UUID vehicleId,
        String userId,
        Instant reservedAt,
        Instant expiresAt,
        ReservationStatus status
) {
    public enum ReservationStatus {
        ACTIVE,
        CANCELLED,
        EXPIRED,
        COMPLETED
    }

    public Reservation {
        Objects.requireNonNull(id, "Reservation id cannot be null");
        Objects.requireNonNull(vehicleId, "Vehicle id cannot be null");
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(reservedAt, "Reserved at cannot be null");
        Objects.requireNonNull(expiresAt, "Expires at cannot be null");
        Objects.requireNonNull(status, "Reservation status cannot be null");
        if (expiresAt.isBefore(reservedAt)) {
            throw new IllegalArgumentException("Expiration time cannot be before reservation time");
        }
    }

    public static Reservation createNew(UUID vehicleId, String userId, Instant now, long durationMinutes) {
        Instant expiresAt = now.plus(durationMinutes, ChronoUnit.MINUTES);
        return new Reservation(UUID.randomUUID(), vehicleId, userId, now, expiresAt, ReservationStatus.ACTIVE);
    }

    public static Reservation createNew(UUID vehicleId, String userId) {
        return createNew(vehicleId, userId, Instant.now(), 15);
    }

    public boolean isExpired(Instant now) {
        return status == ReservationStatus.ACTIVE && now.isAfter(expiresAt);
    }

    public Reservation expire() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservations can be expired, current status: " + status);
        }
        return new Reservation(id, vehicleId, userId, reservedAt, expiresAt, ReservationStatus.EXPIRED);
    }

    public Reservation cancel() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservations can be cancelled, current status: " + status);
        }
        return new Reservation(id, vehicleId, userId, reservedAt, expiresAt, ReservationStatus.CANCELLED);
    }

    public Reservation complete() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservations can be completed, current status: " + status);
        }
        return new Reservation(id, vehicleId, userId, reservedAt, expiresAt, ReservationStatus.COMPLETED);
    }
}
