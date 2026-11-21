package com.urbanmove.fleet.domain.event.payload;

import java.time.Instant;
import java.util.UUID;

public record ReservationCancelledPayload(
        UUID reservationId,
        UUID vehicleId,
        String userId
) {}
