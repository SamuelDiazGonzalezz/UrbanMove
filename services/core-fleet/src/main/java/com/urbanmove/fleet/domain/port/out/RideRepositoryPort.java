package com.urbanmove.fleet.domain.port.out;

import com.urbanmove.fleet.domain.model.Ride;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideRepositoryPort {
    Ride save(Ride ride);
    Optional<Ride> findById(UUID id);
    Optional<Ride> findActiveByReservationId(UUID reservationId);
    List<Ride> findAll();
}
