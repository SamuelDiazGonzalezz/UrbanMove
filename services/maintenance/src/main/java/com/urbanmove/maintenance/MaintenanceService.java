package com.urbanmove.maintenance;

import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Órdenes de mantenimiento y el ciclo de retirada:
 * OPEN → WITHDRAWN (VehicleWithdrawnForMaintenance) → IN_PROGRESS (VehicleChargingStarted / VehicleRepairStarted)
 * → COMPLETED (MaintenanceOrderCompleted, vehículo READY_FOR_SERVICE) → CLOSED (VehicleReturnedToService).
 */
@Service
public class MaintenanceService {

    private final JdbcTemplate jdbc;
    private final EventStore events;
    private final RestClient availability;
    private final int usageLimit;

    public MaintenanceService(JdbcTemplate jdbc, EventStore events, RestClient.Builder builder,
                              @Value("${MAINTENANCE_USAGE_LIMIT:10}") int usageLimit) {
        this(jdbc, events, builder, usageLimit, "http://availability:8080");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MaintenanceService(JdbcTemplate jdbc, EventStore events, RestClient.Builder builder,
                              @Value("${MAINTENANCE_USAGE_LIMIT:10}") int usageLimit,
                              @Value("${AVAILABILITY_URL:http://availability:8080}") String availabilityUrl) {
        this.jdbc = jdbc;
        this.events = events;
        this.availability = builder.baseUrl(availabilityUrl).build();
        this.usageLimit = usageLimit;
    }

    public List<Map<String, Object>> list(String status) {
        if (status != null && !status.isBlank()) {
            return jdbc.queryForList("SELECT * FROM maintenance_order WHERE status = ? ORDER BY created_at DESC", status);
        }
        return jdbc.queryForList("""
                SELECT * FROM maintenance_order
                ORDER BY CASE WHEN status IN ('CLOSED','CANCELLED') THEN 1 ELSE 0 END, created_at DESC LIMIT 200
                """);
    }

    public Map<String, Object> get(String orderId) {
        return jdbc.queryForList("SELECT * FROM maintenance_order WHERE order_id = ?::uuid", orderId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Orden " + orderId));
    }

    /** Crea la orden si no hay otra abierta para el vehículo. Idempotente por {@code sourceEventId}. */
    @Transactional
    public Map<String, Object> open(String vehicleId, String type, String reason, String origin, String sourceEventId) {
        if (!Set.of("CHARGE", "REPAIR").contains(type)) {
            throw ApiException.badRequest("INVALID_TYPE", "El tipo es CHARGE o REPAIR");
        }
        if (sourceEventId != null) {
            Optional<Map<String, Object>> existing = jdbc.queryForList("SELECT * FROM maintenance_order WHERE source_event_id = ?", sourceEventId)
                    .stream().findFirst();
            if (existing.isPresent()) {
                return withResult(existing.get(), "DUPLICATE_EVENT");
            }
        }
        Optional<Map<String, Object>> open = openFor(vehicleId);
        if (open.isPresent()) {
            return withResult(open.get(), "ALREADY_OPEN");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO maintenance_order (order_id, vehicle_id, type, reason, origin, source_event_id, status)
                VALUES (?, ?::uuid, ?, ?, ?, ?, 'OPEN')
                """, id, vehicleId, type, reason, origin, sourceEventId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", id.toString());
        payload.put("vehicleId", vehicleId);
        payload.put("type", type);
        payload.put("reason", reason);
        payload.put("origin", origin);
        events.append("maintenance-order", id.toString(), "MaintenanceOrderOpened", payload);
        return withResult(get(id.toString()), "CREATED");
    }

    /** Cuenta usos (TripRecorded) y abre una revisión al superar el límite. */
    @Transactional
    public Map<String, Object> registerUsage(String vehicleId, String sourceEventId) {
        int inserted = jdbc.update("INSERT INTO usage_event (source_event_id, vehicle_id) VALUES (?, ?::uuid) ON CONFLICT DO NOTHING",
                sourceEventId, vehicleId);
        if (inserted == 0) {
            return Map.of("result", "DUPLICATE_EVENT");
        }
        jdbc.update("""
                INSERT INTO vehicle_usage (vehicle_id, trips_since_revision) VALUES (?::uuid, 1)
                ON CONFLICT (vehicle_id) DO UPDATE SET trips_since_revision = vehicle_usage.trips_since_revision + 1, updated_at = now()
                """, vehicleId);
        Integer trips = jdbc.queryForObject("SELECT trips_since_revision FROM vehicle_usage WHERE vehicle_id = ?::uuid", Integer.class, vehicleId);
        if (trips != null && trips >= usageLimit && openFor(vehicleId).isEmpty()) {
            return open(vehicleId, "REPAIR", "Revisión por uso: " + trips + " viajes desde la última", "AUTOMATIC", "usage-" + sourceEventId);
        }
        return Map.of("result", "COUNTED", "tripsSinceRevision", trips == null ? 0 : trips);
    }

    @Transactional
    public Map<String, Object> withdraw(String orderId, String assignee) {
        Map<String, Object> order = require(orderId, "OPEN");
        String vehicleId = order.get("vehicle_id").toString();
        String state = vehicleState(vehicleId);
        if (!Set.of("AVAILABLE", "LOW_BATTERY", "RESERVED").contains(state)) {
            throw ApiException.conflict("VEHICLE_BUSY", "No se puede retirar: el vehículo está " + state);
        }
        move(orderId, "WITHDRAWN", assignee);
        emit(order, "VehicleWithdrawnForMaintenance", Map.of("reason", order.get("reason").toString()));
        return get(orderId);
    }

    @Transactional
    public Map<String, Object> start(String orderId) {
        Map<String, Object> order = require(orderId, "WITHDRAWN");
        move(orderId, "IN_PROGRESS", null);
        emit(order, "CHARGE".equals(order.get("type")) ? "VehicleChargingStarted" : "VehicleRepairStarted", Map.of());
        return get(orderId);
    }

    @Transactional
    public Map<String, Object> complete(String orderId) {
        Map<String, Object> order = require(orderId, "IN_PROGRESS");
        move(orderId, "COMPLETED", null);
        emit(order, "MaintenanceOrderCompleted", Map.of());
        return get(orderId);
    }

    @Transactional
    public Map<String, Object> returnToService(String orderId, String zoneId) {
        Map<String, Object> order = require(orderId, "COMPLETED");
        if (zoneId == null || zoneId.isBlank()) {
            throw ApiException.badRequest("ZONE_REQUIRED", "Indica la zona donde se deja el vehículo");
        }
        jdbc.update("UPDATE maintenance_order SET status = 'CLOSED', return_zone_id = ?, closed_at = now(), updated_at = now() WHERE order_id = ?::uuid",
                zoneId, orderId);
        jdbc.update("UPDATE vehicle_usage SET trips_since_revision = 0, updated_at = now() WHERE vehicle_id = ?", order.get("vehicle_id"));
        emit(order, "VehicleReturnedToService", Map.of("zoneId", zoneId));
        return get(orderId);
    }

    @Transactional
    public Map<String, Object> cancel(String orderId) {
        Map<String, Object> order = require(orderId, "OPEN");
        jdbc.update("UPDATE maintenance_order SET status = 'CANCELLED', closed_at = now(), updated_at = now() WHERE order_id = ?::uuid", orderId);
        emit(order, "MaintenanceOrderCancelled", Map.of());
        return get(orderId);
    }

    private Map<String, Object> require(String orderId, String status) {
        Map<String, Object> order = get(orderId);
        if (!status.equals(order.get("status"))) {
            throw ApiException.conflict("INVALID_ORDER_STATE", "La orden está en " + order.get("status") + " y la acción requiere " + status);
        }
        return order;
    }

    private void move(String orderId, String status, String assignee) {
        jdbc.update("UPDATE maintenance_order SET status = ?, assignee = COALESCE(?, assignee), updated_at = now() WHERE order_id = ?::uuid",
                status, assignee, orderId);
    }

    private void emit(Map<String, Object> order, String type, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.get("order_id").toString());
        payload.put("vehicleId", order.get("vehicle_id").toString());
        payload.put("type", order.get("type"));
        payload.putAll(extra);
        events.append("maintenance-order", order.get("order_id").toString(), type, payload);
    }

    private Optional<Map<String, Object>> openFor(String vehicleId) {
        return jdbc.queryForList("SELECT * FROM maintenance_order WHERE vehicle_id = ?::uuid AND status IN ('OPEN','WITHDRAWN','IN_PROGRESS','COMPLETED')",
                vehicleId).stream().findFirst();
    }

    @SuppressWarnings("unchecked")
    private String vehicleState(String vehicleId) {
        try {
            Map<String, Object> vehicle = availability.get().uri("/api/availability/{id}", vehicleId).retrieve().body(Map.class);
            return vehicle == null ? "UNKNOWN" : String.valueOf(vehicle.get("state"));
        } catch (Exception e) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "AVAILABILITY_DOWN",
                    "No se pudo comprobar el estado del vehículo; inténtalo de nuevo");
        }
    }

    private static Map<String, Object> withResult(Map<String, Object> order, String result) {
        Map<String, Object> copy = new LinkedHashMap<>(order);
        copy.put("result", result);
        return copy;
    }
}
