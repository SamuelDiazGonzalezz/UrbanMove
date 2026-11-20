package com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository;

import com.urbanmove.fleet.domain.model.Ride;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.RideJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataRideRepository extends JpaRepository<RideJpaEntity, UUID> {
    Optional<RideJpaEntity> findByReservationIdAndStatus(UUID reservationId, Ride.RideStatus status);
    Optional<RideJpaEntity> findByVehicleIdAndStatus(UUID vehicleId, Ride.RideStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RideJpaEntity r WHERE r.id = :id")
    Optional<RideJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
