package com.urbanmove.fleet.domain.port.in;

import com.urbanmove.fleet.domain.model.Reservation;
import java.util.UUID;

public interface ReserveVehicleUseCase {
    Reservation reserve(UUID vehicleId, String userId);
}
