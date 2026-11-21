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
        LEFT JOIN event_publications p ON o.id = p.event_id
        GROUP BY o.id
        HAVING bool_or(p.status = 'SUCCESS') IS NOT TRUE
           AND bool_or(p.status = 'DEAD_LETTER') IS NOT TRUE
        ORDER BY o.created_at ASC
        LIMIT :batchSize
        FOR UPDATE OF o SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEventJpaEntity> findPendingEventsForClaim(@Param("batchSize") int batchSize);
}
