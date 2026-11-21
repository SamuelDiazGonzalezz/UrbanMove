package com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository;

import com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity.EventPublicationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataEventPublicationRepository extends JpaRepository<EventPublicationJpaEntity, UUID> {

    @Query("SELECT MAX(p.attemptNumber) FROM EventPublicationJpaEntity p WHERE p.eventId = :eventId")
    Optional<Integer> findMaxAttemptNumberByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(p) > 0 FROM EventPublicationJpaEntity p WHERE p.eventId = :eventId AND p.status = 'SUCCESS'")
    boolean isEventSuccessfullyPublished(@Param("eventId") UUID eventId);
}
