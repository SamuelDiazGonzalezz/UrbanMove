package com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository;

import com.urbanmove.fleet.domain.model.Ride;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.RideJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataRideRepository extends JpaRepository<RideJpaEntity, UUID> {
    Optional<RideJpaEntity> findByReservationIdAndStatus(UUID reservationId, Ride.RideStatus status);
}
