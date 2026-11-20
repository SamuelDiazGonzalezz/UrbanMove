package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.port.in.GetVehiclesUseCase;
import com.urbanmove.fleet.domain.port.in.ManageVehicleAvailabilityUseCase;
import com.urbanmove.fleet.domain.port.out.VehicleRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class VehicleAvailabilityService implements GetVehiclesUseCase, ManageVehicleAvailabilityUseCase {

    private final VehicleRepositoryPort vehicleRepository;

    public VehicleAvailabilityService(VehicleRepositoryPort vehicleRepository) {
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository, "vehicleRepository cannot be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getAvailableVehicles() {
        return vehicleRepository.findAvailable();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> getVehicleById(UUID id) {
        return vehicleRepository.findById(id);
    }
}
