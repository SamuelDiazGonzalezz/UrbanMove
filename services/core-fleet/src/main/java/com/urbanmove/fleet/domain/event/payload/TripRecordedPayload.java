package com.urbanmove.fleet.domain.event.payload;

import java.time.Instant;
import java.util.UUID;

public record TripRecordedPayload(
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
) {}
