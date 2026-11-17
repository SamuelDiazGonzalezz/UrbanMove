package com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository;

import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.VehicleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SpringDataVehicleRepository extends JpaRepository<VehicleJpaEntity, UUID> {
    List<VehicleJpaEntity> findByStatusAndBatteryPercentageGreaterThanEqual(VehicleStatus status, int minBattery);
}
