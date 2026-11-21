package com.urbanmove.fleet.domain.event.payload;

import java.time.Instant;
import java.util.UUID;

public record VehicleReservedPayload(
        UUID reservationId,
        UUID vehicleId,
        String vehicleCode,
        String userId,
        Instant expiresAt
) {}
