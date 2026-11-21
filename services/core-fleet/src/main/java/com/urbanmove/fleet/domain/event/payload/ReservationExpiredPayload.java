package com.urbanmove.fleet.domain.event.payload;

import java.util.UUID;

public record ReservationExpiredPayload(
        UUID reservationId,
        UUID vehicleId,
        String userId
) {}
