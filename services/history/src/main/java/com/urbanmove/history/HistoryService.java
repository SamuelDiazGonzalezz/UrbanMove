package com.urbanmove.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.common.events.EventEnvelope;
import com.urbanmove.common.events.EventHandler;
import com.urbanmove.common.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Consume todos los eventos y ofrece la consulta de historial por usuario, vehículo o flujo. */
@Service
@RestController
public class HistoryService implements EventHandler {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public HistoryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("*");
    }

    @Override
    public void handle(EventEnvelope e) {
        JsonNode p = e.payload();
        String zone = p.hasNonNull("zoneId") ? p.get("zoneId").asText()
                : p.path("destination").hasNonNull("zoneId") ? p.path("destination").get("zoneId").asText()
                : p.hasNonNull("toZoneId") ? p.get("toZoneId").asText() : null;
        try {
            jdbc.update("""
                    INSERT INTO history_entry (event_id, event_type, occurred_at, producer, correlation_id, user_id, vehicle_id, zone_id, payload)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING
                    """, UUID.fromString(e.eventId()), e.eventType(), Timestamp.from(e.occurredAt()), e.producer(), e.correlationId(),
                    e.text("userId"), e.text("vehicleId"), zone, objectMapper.writeValueAsString(p));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo guardar " + e.eventId(), ex);
        }
    }

    @GetMapping("/api/history")
    public List<Map<String, Object>> query(@RequestParam(required = false) String userId,
                                           @RequestParam(required = false) String vehicleId,
                                           @RequestParam(required = false) String correlationId,
                                           @RequestParam(required = false) String eventType,
                                           @RequestParam(defaultValue = "100") int limit) {
        if (!CurrentUser.isStaff()) {
            userId = CurrentUser.id();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT event_id, event_type, occurred_at, producer, correlation_id, user_id, vehicle_id, zone_id, payload::text AS payload
                FROM history_entry WHERE 1 = 1""");
        List<Object> args = new ArrayList<>();
        if (userId != null && !userId.isBlank()) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        if (vehicleId != null && !vehicleId.isBlank()) {
            sql.append(" AND vehicle_id = ?");
            args.add(vehicleId);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            sql.append(" AND correlation_id = ?");
            args.add(correlationId);
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event_type = ?");
            args.add(eventType);
        }
        sql.append(" ORDER BY occurred_at DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 500));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @GetMapping("/api/history/stats")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public List<Map<String, Object>> stats() {
        return jdbc.queryForList("""
                SELECT event_type, count(*) AS total, max(occurred_at) AS last_seen
                FROM history_entry GROUP BY event_type ORDER BY total DESC
                """);
    }
}
