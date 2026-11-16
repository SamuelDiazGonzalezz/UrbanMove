package com.urbanmove.fleet.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Ride(
        UUID id,
        UUID reservationId,
        UUID vehicleId,
        String userId,
        Instant startedAt,
        Instant finishedAt,
        double startLatitude,
        double startLongitude,
        double endLatitude,
        double endLongitude,
        double distanceKm,
        double costAmount,
        RideStatus status
) {
    public enum RideStatus {
        IN_PROGRESS,
        COMPLETED
    }

    public Ride {
        Objects.requireNonNull(id, "Ride id cannot be null");
        Objects.requireNonNull(reservationId, "Reservation id cannot be null");
        Objects.requireNonNull(vehicleId, "Vehicle id cannot be null");
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(startedAt, "Started at cannot be null");
        Objects.requireNonNull(status, "Ride status cannot be null");
    }

    public static Ride start(UUID reservationId, UUID vehicleId, String userId, double lat, double lon) {
        return new Ride(
                UUID.randomUUID(),
                reservationId,
                vehicleId,
                userId,
                Instant.now(),
                null,
                lat,
                lon,
                0.0,
                0.0,
                0.0,
                0.0,
                RideStatus.IN_PROGRESS
        );
    }

    public Ride complete(double endLat, double endLon) {
        if (this.status != RideStatus.IN_PROGRESS) {
            throw new IllegalStateException("Ride is not in progress");
        }
        Instant end = Instant.now();
        // Distancia aproximada y coste: 1.0€ base + 0.25€ por minuto simulado
        double dist = Math.max(0.5, Math.hypot(endLat - startLatitude, endLon - startLongitude) * 111.0);
        long seconds = Math.max(60, end.getEpochSecond() - startedAt.getEpochSecond());
        double cost = Math.round((1.0 + (seconds / 60.0) * 0.25) * 100.0) / 100.0;

        return new Ride(
                id,
                reservationId,
                vehicleId,
                userId,
                startedAt,
                end,
                startLatitude,
                startLongitude,
                endLat,
                endLon,
                Math.round(dist * 100.0) / 100.0,
                cost,
                RideStatus.COMPLETED
        );
    }
}
