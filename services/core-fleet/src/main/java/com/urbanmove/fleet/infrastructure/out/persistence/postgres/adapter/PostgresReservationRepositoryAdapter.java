package com.urbanmove.fleet.infrastructure.out.persistence.postgres.adapter;

import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.ReservationJpaEntity;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataReservationRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresReservationRepositoryAdapter implements ReservationRepositoryPort {

    private final SpringDataReservationRepository repository;

    public PostgresReservationRepositoryAdapter(SpringDataReservationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Reservation save(Reservation reservation) {
        ReservationJpaEntity saved = repository.save(ReservationJpaEntity.fromDomain(reservation));
        return saved.toDomain();
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return repository.findById(id).map(ReservationJpaEntity::toDomain);
    }

    @Override
    public Optional<Reservation> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(ReservationJpaEntity::toDomain);
    }

    @Override
    public List<Reservation> findExpiredActive(Instant now) {
        return repository.findByStatusAndExpiresAtBefore(Reservation.ReservationStatus.ACTIVE, now)
                .stream()
                .map(ReservationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Reservation> findActiveByVehicleId(UUID vehicleId) {
        return repository.findByVehicleIdAndStatus(vehicleId, Reservation.ReservationStatus.ACTIVE)
                .map(ReservationJpaEntity::toDomain);
    }
}
