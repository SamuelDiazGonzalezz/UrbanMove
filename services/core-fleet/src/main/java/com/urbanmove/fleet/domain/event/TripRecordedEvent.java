package com.urbanmove.fleet.domain.event;

import java.time.Instant;
import java.util.UUID;

public record TripRecordedEvent(
        UUID eventId,
        UUID rideId,
        UUID reservationId,
        UUID vehicleId,
        String vehicleCode,
        String userId,
        Instant startedAt,
        Instant finishedAt,
        double distanceKm,
        double costAmount,
        double endLatitude,
        double endLongitude
) {
    public static TripRecordedEvent of(
            UUID rideId, UUID reservationId, UUID vehicleId, String vehicleCode,
            String userId, Instant startedAt, Instant finishedAt,
            double distanceKm, double costAmount, double endLat, double endLon
    ) {
        return new TripRecordedEvent(
                UUID.randomUUID(), rideId, reservationId, vehicleId, vehicleCode,
                userId, startedAt, finishedAt, distanceKm, costAmount, endLat, endLon
        );
    }
}
