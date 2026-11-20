package com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository;

import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.VehicleJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataVehicleRepository extends JpaRepository<VehicleJpaEntity, UUID> {
    List<VehicleJpaEntity> findByStatusAndBatteryPercentageGreaterThanEqual(VehicleStatus status, int minBattery);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VehicleJpaEntity v WHERE v.id = :id")
    Optional<VehicleJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
