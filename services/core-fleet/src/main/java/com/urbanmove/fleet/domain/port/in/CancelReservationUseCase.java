package com.urbanmove.fleet.domain.port.in;

import com.urbanmove.fleet.domain.model.Reservation;
import java.util.UUID;

public interface CancelReservationUseCase {
    Reservation cancelReservation(UUID reservationId);
}
