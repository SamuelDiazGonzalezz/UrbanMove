package com.urbanmove.fleet.infrastructure.out.persistence.postgres.adapter;

import com.urbanmove.fleet.domain.model.Ride;
import com.urbanmove.fleet.domain.port.out.RideRepositoryPort;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.RideJpaEntity;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataRideRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresRideRepositoryAdapter implements RideRepositoryPort {

    private final SpringDataRideRepository repository;

    public PostgresRideRepositoryAdapter(SpringDataRideRepository repository) {
        this.repository = repository;
    }

    @Override
    public Ride save(Ride ride) {
        return repository.save(RideJpaEntity.fromDomain(ride)).toDomain();
    }

    @Override
    public Optional<Ride> findById(UUID id) {
        return repository.findById(id).map(RideJpaEntity::toDomain);
    }

    @Override
    public Optional<Ride> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(RideJpaEntity::toDomain);
    }

    @Override
    public Optional<Ride> findActiveByReservationId(UUID reservationId) {
        return repository.findByReservationIdAndStatus(reservationId, Ride.RideStatus.IN_PROGRESS)
                .map(RideJpaEntity::toDomain);
    }

    @Override
    public Optional<Ride> findActiveByVehicleId(UUID vehicleId) {
        return repository.findByVehicleIdAndStatus(vehicleId, Ride.RideStatus.IN_PROGRESS)
                .map(RideJpaEntity::toDomain);
    }

    @Override
    public List<Ride> findAll() {
        return repository.findAll().stream().map(RideJpaEntity::toDomain).toList();
    }
}
