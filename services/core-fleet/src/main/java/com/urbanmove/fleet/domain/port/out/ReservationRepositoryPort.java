package com.urbanmove.fleet.domain.port.out;

import com.urbanmove.fleet.domain.model.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepositoryPort {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(UUID id);
    Optional<Reservation> findByIdForUpdate(UUID id);
    List<Reservation> findExpiredActive(Instant now);
    Optional<Reservation> findActiveByVehicleId(UUID vehicleId);
}
