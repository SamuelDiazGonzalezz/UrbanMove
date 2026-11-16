package com.urbanmove.fleet.domain.port.in;

import com.urbanmove.fleet.domain.model.Ride;
import java.util.List;
import java.util.UUID;

public interface ManageRidesUseCase {
    Ride startRide(UUID reservationId);
    Ride finishRide(UUID rideId, double endLatitude, double endLongitude);
    List<Ride> getAllRides();
}
