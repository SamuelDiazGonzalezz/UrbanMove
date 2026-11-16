package com.urbanmove.fleet.domain.port.out;

import com.urbanmove.fleet.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepositoryPort {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(UUID id);
}
