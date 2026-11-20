package com.urbanmove.fleet.domain.port.in;

import com.urbanmove.fleet.domain.model.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManageVehicleAvailabilityUseCase {
    List<Vehicle> getAllVehicles();
    List<Vehicle> getAvailableVehicles();
    Optional<Vehicle> getVehicleById(UUID id);
}
