package com.urbanmove.fleet.infrastructure.out.persistence.postgres.adapter;

import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.port.out.VehicleRepositoryPort;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.VehicleJpaEntity;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataVehicleRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresVehicleRepositoryAdapter implements VehicleRepositoryPort {

    private final SpringDataVehicleRepository repository;

    public PostgresVehicleRepositoryAdapter(SpringDataVehicleRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Vehicle> findAll() {
        return repository.findAll().stream().map(VehicleJpaEntity::toDomain).toList();
    }

    @Override
    public List<Vehicle> findAvailable() {
        return repository.findByStatusAndBatteryPercentageGreaterThanEqual(VehicleStatus.AVAILABLE, 15)
                .stream()
                .map(VehicleJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return repository.findById(id).map(VehicleJpaEntity::toDomain);
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        VehicleJpaEntity saved = repository.save(VehicleJpaEntity.fromDomain(vehicle));
        return saved.toDomain();
    }
}
