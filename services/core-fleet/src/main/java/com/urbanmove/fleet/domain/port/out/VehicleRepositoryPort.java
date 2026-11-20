package com.urbanmove.fleet.domain.port.out;

import com.urbanmove.fleet.domain.model.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepositoryPort {
    List<Vehicle> findAll();
    List<Vehicle> findAvailable();
    Optional<Vehicle> findById(UUID id);
    Optional<Vehicle> findByIdForUpdate(UUID id);
    Vehicle save(Vehicle vehicle);
}
