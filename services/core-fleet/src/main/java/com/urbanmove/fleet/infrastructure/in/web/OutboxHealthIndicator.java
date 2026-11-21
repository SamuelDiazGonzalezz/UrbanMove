package com.urbanmove.fleet.infrastructure.in.web;

import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataEventPublicationRepository;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.repository.SpringDataOutboxRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * HealthIndicator para monitorizar el estado de salud del Outbox transaccional.
 */
@Component
public class OutboxHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public OutboxHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Long totalOutbox = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Long.class);
            Long pendingCount = jdbcTemplate.queryForObject("""
                SELECT count(DISTINCT o.id) FROM outbox_events o
                LEFT JOIN event_publications p ON o.id = p.event_id
                GROUP BY o.id
                HAVING bool_or(p.status = 'SUCCESS') IS NOT TRUE 
                   AND bool_or(p.status = 'DEAD_LETTER') IS NOT TRUE
                """, Long.class);
            Long deadLetterCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM event_publications WHERE status = 'DEAD_LETTER'", Long.class);

            long pending = pendingCount != null ? pendingCount : 0L;
            long dead = deadLetterCount != null ? deadLetterCount : 0L;

            Health.Builder builder = (dead > 0) ? Health.down() : Health.up();
            return builder
                    .withDetail("totalEvents", totalOutbox != null ? totalOutbox : 0L)
                    .withDetail("pendingEvents", pending)
                    .withDetail("deadLetterEvents", dead)
                    .build();
        } catch (Exception e) {
            return Health.unknown().withException(e).build();
        }
    }
}
