package com.urbanmove.relocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.urbanmove.common.events.EventEnvelope;
import com.urbanmove.common.events.EventHandler;
import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Órdenes de recolocación: se crean a partir de {@code DemandHotspotDetected} (Lambda
 * demand-relocation) o a mano, y operaciones las asigna, inicia y completa.
 */
@Service
public class RelocationService implements EventHandler {

    private final JdbcTemplate jdbc;
    private final EventStore events;

    public RelocationService(JdbcTemplate jdbc, EventStore events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                SELECT * FROM relocation_order
                ORDER BY CASE WHEN status IN ('COMPLETED','CANCELLED') THEN 1 ELSE 0 END, created_at DESC LIMIT 200
                """);
    }

    public Map<String, Object> get(String orderId) {
        return jdbc.queryForList("SELECT * FROM relocation_order WHERE order_id = ?::uuid", orderId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Orden de recolocación " + orderId));
    }

    @Transactional
    public Map<String, Object> create(String fromZone, String toZone, int quantity, String origin, String reason, String sourceEventId) {
        if (fromZone == null || toZone == null || fromZone.equals(toZone)) {
            throw ApiException.badRequest("INVALID_ZONES", "Indica dos zonas distintas");
        }
        Integer open = jdbc.queryForObject("SELECT count(*) FROM relocation_order WHERE to_zone_id = ? AND status IN ('CREATED','ASSIGNED','IN_PROGRESS')",
                Integer.class, toZone);
        if (open != null && open > 0) {
            throw ApiException.conflict("ORDER_ALREADY_OPEN", "Ya hay una orden abierta hacia " + toZone);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO relocation_order (order_id, from_zone_id, to_zone_id, quantity, status, origin, reason, source_event_id)
                VALUES (?, ?, ?, ?, 'CREATED', ?, ?, ?)
                """, id, fromZone, toZone, Math.max(1, quantity), origin, reason, sourceEventId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", id.toString());
        payload.put("fromZoneId", fromZone);
        payload.put("toZoneId", toZone);
        payload.put("quantity", Math.max(1, quantity));
        payload.put("origin", origin);
        payload.put("reason", reason);
        events.append("relocation-order", id.toString(), "RelocationOrderCreated", payload);
        return get(id.toString());
    }

    @Transactional
    public Map<String, Object> assign(String orderId, String assignee) {
        Map<String, Object> order = require(orderId, Set.of("CREATED"));
        jdbc.update("UPDATE relocation_order SET status = 'ASSIGNED', assignee = ?, updated_at = now() WHERE order_id = ?::uuid", assignee, orderId);
        emit(order, "RelocationOrderAssigned", Map.of("assignee", assignee));
        return get(orderId);
    }

    @Transactional
    public Map<String, Object> start(String orderId) {
        Map<String, Object> order = require(orderId, Set.of("ASSIGNED"));
        jdbc.update("UPDATE relocation_order SET status = 'IN_PROGRESS', updated_at = now() WHERE order_id = ?::uuid", orderId);
        emit(order, "RelocationOrderStarted", Map.of());
        return get(orderId);
    }

    @Transactional
    public Map<String, Object> complete(String orderId, List<String> vehicleIds) {
        Map<String, Object> order = require(orderId, Set.of("IN_PROGRESS"));
        List<String> moved = vehicleIds == null ? List.of() : vehicleIds;
        jdbc.update("UPDATE relocation_order SET status = 'COMPLETED', moved_vehicle_ids = ?, completed_at = now(), updated_at = now() WHERE order_id = ?::uuid",
                String.join(",", moved), orderId);
        long minutes = Duration.between(((Timestamp) order.get("created_at")).toInstant(), java.time.Instant.now()).toMinutes();
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("vehicleIds", moved);
        extra.put("quantityMoved", moved.size());
        extra.put("minutesToComplete", minutes);
        emit(order, "RelocationOrderCompleted", extra);
        return get(orderId);
    }

    @Transactional
    public Map<String, Object> cancel(String orderId) {
        Map<String, Object> order = require(orderId, Set.of("CREATED", "ASSIGNED"));
        jdbc.update("UPDATE relocation_order SET status = 'CANCELLED', updated_at = now() WHERE order_id = ?::uuid", orderId);
        emit(order, "RelocationOrderCancelled", Map.of());
        return get(orderId);
    }

    private Map<String, Object> require(String orderId, Set<String> allowed) {
        Map<String, Object> order = get(orderId);
        if (!allowed.contains(order.get("status").toString())) {
            throw ApiException.conflict("INVALID_ORDER_STATE", "La orden está en " + order.get("status"));
        }
        return order;
    }

    private void emit(Map<String, Object> order, String type, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.get("order_id").toString());
        payload.put("fromZoneId", order.get("from_zone_id"));
        payload.put("toZoneId", order.get("to_zone_id"));
        payload.put("quantity", order.get("quantity"));
        payload.putAll(extra);
        events.append("relocation-order", order.get("order_id").toString(), type, payload);
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("DemandHotspotDetected");
    }

    @Override
    public void handle(EventEnvelope e) {
        Integer open = jdbc.queryForObject("SELECT count(*) FROM relocation_order WHERE to_zone_id = ? AND status IN ('CREATED','ASSIGNED','IN_PROGRESS')",
                Integer.class, e.text("zoneId"));
        if ((open != null && open > 0) || e.text("sourceZoneId") == null) {
            return;
        }
        List<String> metrics = new ArrayList<>();
        JsonNode p = e.payload();
        metrics.add("disponibles " + p.path("available").asInt());
        metrics.add("demanda última hora " + p.path("demandLastHour").asInt());
        create(e.text("sourceZoneId"), e.text("zoneId"), e.integer("suggestedQuantity") == null ? 1 : e.integer("suggestedQuantity"),
                "AUTOMATIC", "Déficit detectado: " + String.join(", ", metrics), e.eventId());
    }
}
