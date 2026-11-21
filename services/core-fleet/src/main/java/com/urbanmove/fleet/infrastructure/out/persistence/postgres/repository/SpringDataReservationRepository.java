package com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository;

import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.ReservationJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataReservationRepository extends JpaRepository<ReservationJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReservationJpaEntity r WHERE r.id = :id")
    Optional<ReservationJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT * FROM reservations WHERE status = :status AND expires_at < :now FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<ReservationJpaEntity> findByStatusAndExpiresAtBeforeWithSkipLocked(@Param("status") String status, @Param("now") Instant now);

    Optional<ReservationJpaEntity> findByVehicleIdAndStatus(UUID vehicleId, Reservation.ReservationStatus status);
}
