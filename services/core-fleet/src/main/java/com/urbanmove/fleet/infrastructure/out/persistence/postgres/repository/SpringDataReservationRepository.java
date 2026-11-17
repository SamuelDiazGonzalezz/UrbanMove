package com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository;

import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.ReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SpringDataReservationRepository extends JpaRepository<ReservationJpaEntity, UUID> {
}
