package com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository;

import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Reclama eventos pendientes con SKIP LOCKED para permitir múltiples relays concurrentes sin colisiones.
     * Retorna eventos que no tienen publicación con status SUCCESS ni DEAD_LETTER.
     */
    @Query(value = """
        SELECT o.* FROM outbox_events o
        WHERE NOT EXISTS (
            SELECT 1 FROM event_publications p
            WHERE p.event_id = o.id
              AND p.status IN ('SUCCESS', 'DEAD_LETTER')
        )
        ORDER BY o.created_at ASC
        LIMIT :batchSize
        FOR UPDATE OF o SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEventJpaEntity> findPendingEventsForClaim(@Param("batchSize") int batchSize);
}
