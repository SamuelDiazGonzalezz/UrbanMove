package com.urbanmove.availability;

import com.urbanmove.common.events.EventEnvelope;
import com.urbanmove.common.events.EventHandler;
import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.geo.Geo;
import com.urbanmove.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.urbanmove.availability.VehicleState.*;

/**
 * Dueño del estado operativo de cada vehículo. Consume los eventos de los demás servicios,
 * valida la transición y publica {@code VehicleAvailabilityChanged}. Las transiciones no
 * válidas se registran como {@code VehicleTransitionRejected} (o {@code VehicleReservationRejected}).
 */
@Service
public class AvailabilityService implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);
    static final int LOW_BATTERY_PCT = 20;

    public record Vehicle(String vehicleId, String code, String vehicleType, String state, String zoneId,
                          Double latitude, Double longitude, Integer batteryPct, String reservationId,
                          String userId, int version, Instant updatedAt) {

        /** Proyección pública que oculta datos personales de reserva/uso para clientes no staff. */
        public Vehicle toPublicView() {
            return new Vehicle(vehicleId, code, vehicleType, state, zoneId,
                    latitude, longitude, batteryPct, null, null, version, updatedAt);
        }
    }

    record ZoneRef(String zoneId, String name, double latitude, double longitude) {
    }

    private static final RowMapper<Vehicle> MAPPER = (rs, i) -> new Vehicle(
            rs.getString("vehicle_id"), rs.getString("code"), rs.getString("vehicle_type"), rs.getString("state"),
            rs.getString("zone_id"), (Double) rs.getObject("latitude"), (Double) rs.getObject("longitude"),
            (Integer) rs.getObject("battery_pct"), rs.getString("reservation_id"), rs.getString("user_id"),
            rs.getInt("version"), rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;
    private final EventStore events;

    public AvailabilityService(JdbcTemplate jdbc, EventStore events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    // ---------------------------------------------------------------- consultas

    public List<Vehicle> list(String zoneId, String state) {
        StringBuilder sql = new StringBuilder("SELECT * FROM vehicle_state WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (zoneId != null && !zoneId.isBlank()) {
            sql.append(" AND zone_id = ?");
            args.add(zoneId);
        }
        if (state != null && !state.isBlank()) {
            sql.append(" AND state = ?");
            args.add(state);
        }
        sql.append(" ORDER BY code");
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public Vehicle get(String vehicleId) {
        return find(vehicleId).orElseThrow(() -> ApiException.notFound("Vehículo " + vehicleId));
    }

    public List<Map<String, Object>> summary() {
        return jdbc.queryForList("""
                SELECT z.zone_id, z.name,
                       count(v.*) FILTER (WHERE v.state = 'AVAILABLE')         AS available,
                       count(v.*) FILTER (WHERE v.state = 'RESERVED')          AS reserved,
                       count(v.*) FILTER (WHERE v.state = 'IN_USE')            AS in_use,
                       count(v.*) FILTER (WHERE v.state = 'LOW_BATTERY')       AS low_battery,
                       count(v.*) FILTER (WHERE v.state IN ('WITHDRAWN','CHARGING','REPAIRING','READY_FOR_SERVICE')) AS maintenance,
                       count(v.*) FILTER (WHERE v.state <> 'RETIRED')          AS total
                FROM zone_ref z LEFT JOIN vehicle_state v ON v.zone_id = z.zone_id
                GROUP BY z.zone_id, z.name ORDER BY z.name
                """);
    }

    private Optional<Vehicle> find(String vehicleId) {
        return jdbc.query("SELECT * FROM vehicle_state WHERE vehicle_id = ?::uuid", MAPPER, vehicleId).stream().findFirst();
    }

    // ---------------------------------------------------------------- eventos

    @Override
    public Set<String> eventTypes() {
        return Set.of("ZoneCreated", "VehiclePurchased", "VehicleRetired", "VehicleReserved", "ReservationCancelled",
                "ReservationExpired", "VehiclePickedUp", "VehicleDroppedOff", "TelemetryReceived", "VehicleBatteryLow",
                "VehicleWithdrawnForMaintenance", "VehicleChargingStarted", "VehicleRepairStarted",
                "MaintenanceOrderCompleted", "VehicleReturnedToService", "RelocationOrderCompleted");
    }

    @Override
    public void handle(EventEnvelope e) {
        switch (e.eventType()) {
            case "ZoneCreated" -> jdbc.update("""
                    INSERT INTO zone_ref (zone_id, name, latitude, longitude) VALUES (?, ?, ?, ?)
                    ON CONFLICT (zone_id) DO UPDATE SET name = EXCLUDED.name, latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude
                    """, e.text("zoneId"), e.text("name"), e.decimal("latitude"), e.decimal("longitude"));
            case "VehiclePurchased" -> onPurchased(e);
            case "VehicleRetired" -> transition(e, EnumSet.complementOf(EnumSet.of(IN_USE, RETIRED)), RETIRED, Change.clearAssignment());
            case "VehicleReserved" -> onReserved(e);
            case "ReservationCancelled", "ReservationExpired" -> onReservationReleased(e);
            case "VehiclePickedUp" -> transition(e, EnumSet.of(AVAILABLE, RESERVED), IN_USE,
                    Change.of().battery(e.integer("batteryPct")).user(e.text("userId")).position(e.decimal("latitude"), e.decimal("longitude")));
            case "VehicleDroppedOff" -> onDroppedOff(e);
            case "TelemetryReceived" -> onTelemetry(e);
            case "VehicleBatteryLow" -> onBatteryLow(e);
            case "VehicleWithdrawnForMaintenance" -> transition(e, EnumSet.of(AVAILABLE, LOW_BATTERY, RESERVED), WITHDRAWN, Change.clearAssignment());
            case "VehicleChargingStarted" -> transition(e, EnumSet.of(WITHDRAWN), CHARGING, Change.of());
            case "VehicleRepairStarted" -> transition(e, EnumSet.of(WITHDRAWN), REPAIRING, Change.of());
            case "MaintenanceOrderCompleted" -> onMaintenanceCompleted(e);
            case "VehicleReturnedToService" -> onReturned(e);
            case "RelocationOrderCompleted" -> onRelocated(e);
            default -> log.debug("Evento ignorado {}", e.eventType());
        }
    }

    private void onPurchased(EventEnvelope e) {
        String vehicleId = e.text("vehicleId");
        if (find(vehicleId).isPresent()) {
            return;
        }
        jdbc.update("""
                INSERT INTO vehicle_state (vehicle_id, code, vehicle_type, state, zone_id, latitude, longitude, battery_pct)
                VALUES (?::uuid, ?, ?, 'AVAILABLE', ?, ?, ?, ?)
                """, vehicleId, e.text("code"), e.text("vehicleType"), e.text("zoneId"),
                e.decimal("latitude"), e.decimal("longitude"), e.integer("batteryPct"));
        publishChange(get(vehicleId), null, e, "PURCHASED");
    }

    private void onReserved(EventEnvelope e) {
        Optional<Vehicle> vehicle = find(e.text("vehicleId"));
        if (vehicle.isEmpty() || !vehicle.get().state().equals(AVAILABLE.name())) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reservationId", e.text("reservationId"));
            payload.put("vehicleId", e.text("vehicleId"));
            payload.put("userId", e.text("userId"));
            payload.put("currentState", vehicle.map(Vehicle::state).orElse("UNKNOWN"));
            events.append("vehicle-availability", e.text("vehicleId"), "VehicleReservationRejected", payload);
            return;
        }
        transition(e, EnumSet.of(AVAILABLE), RESERVED, Change.of().reservation(e.text("reservationId"), e.text("userId")));
    }

    private void onReservationReleased(EventEnvelope e) {
        Optional<Vehicle> vehicle = find(e.text("vehicleId"));
        if (vehicle.isPresent() && vehicle.get().state().equals(RESERVED.name())
                && java.util.Objects.equals(e.text("reservationId"), vehicle.get().reservationId())) {
            transition(e, EnumSet.of(RESERVED), AVAILABLE, Change.clearAssignment());
        }
    }

    private void onDroppedOff(EventEnvelope e) {
        Integer battery = e.integer("batteryPct");
        VehicleState target = battery != null && battery < LOW_BATTERY_PCT ? LOW_BATTERY : AVAILABLE;
        Double lat = e.decimal("latitude");
        Double lon = e.decimal("longitude");
        transition(e, EnumSet.of(IN_USE), target,
                Change.clearAssignment().battery(battery).position(lat, lon).zone(nearestZone(lat, lon)));
    }

    private void onTelemetry(EventEnvelope e) {
        Optional<Vehicle> vehicle = find(e.text("vehicleId"));
        if (vehicle.isEmpty()) {
            return;
        }
        Integer battery = e.integer("batteryPct");
        Double lat = e.decimal("latitude");
        Double lon = e.decimal("longitude");
        jdbc.update("""
                UPDATE vehicle_state SET battery_pct = COALESCE(?, battery_pct), latitude = COALESCE(?, latitude),
                       longitude = COALESCE(?, longitude), version = version + 1, updated_at = now()
                WHERE vehicle_id = ?::uuid
                """, battery, lat, lon, e.text("vehicleId"));
    }

    private void onBatteryLow(EventEnvelope e) {
        Optional<Vehicle> vehicle = find(e.text("vehicleId"));
        if (vehicle.isPresent() && vehicle.get().state().equals(AVAILABLE.name())) {
            transition(e, EnumSet.of(AVAILABLE), LOW_BATTERY, Change.of().battery(e.integer("batteryPct")));
        }
        // En IN_USE el estado cambia al devolverlo (VehicleDroppedOff); en mantenimiento no aplica.
    }

    private void onMaintenanceCompleted(EventEnvelope e) {
        Change change = Change.of();
        if ("CHARGE".equals(e.text("type"))) {
            find(e.text("vehicleId")).filter(v -> v.batteryPct() != null).ifPresent(v -> change.battery(100));
        }
        transition(e, EnumSet.of(CHARGING, REPAIRING), READY_FOR_SERVICE, change);
    }

    private void onReturned(EventEnvelope e) {
        String zoneId = e.text("zoneId");
        Change change = Change.of();
        zone(zoneId).ifPresent(z -> change.zone(z.zoneId()).position(jitter(z.latitude()), jitter(z.longitude())));
        transition(e, EnumSet.of(READY_FOR_SERVICE), AVAILABLE, change);
    }

    private void onRelocated(EventEnvelope e) {
        String toZone = e.text("toZoneId");
        Optional<ZoneRef> zone = zone(toZone);
        if (zone.isEmpty() || e.payload().get("vehicleIds") == null) {
            return;
        }
        e.payload().get("vehicleIds").forEach(node -> find(node.asText()).ifPresent(v -> {
            if (v.state().equals(AVAILABLE.name()) || v.state().equals(LOW_BATTERY.name())) {
                applyChange(v, VehicleState.valueOf(v.state()),
                        Change.of().zone(zone.get().zoneId()).position(jitter(zone.get().latitude()), jitter(zone.get().longitude())), e, "RELOCATED");
            }
        }));
    }

    // ---------------------------------------------------------------- núcleo de transición

    private void transition(EventEnvelope e, Set<VehicleState> allowedFrom, VehicleState to, Change change) {
        String vehicleId = e.text("vehicleId");
        Optional<Vehicle> current = find(vehicleId);
        if (current.isEmpty()) {
            log.warn("{} sobre vehículo desconocido {}", e.eventType(), vehicleId);
            return;
        }
        VehicleState from = VehicleState.valueOf(current.get().state());
        if (!allowedFrom.contains(from)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("vehicleId", vehicleId);
            payload.put("currentState", from.name());
            payload.put("requestedState", to.name());
            payload.put("triggerEventType", e.eventType());
            payload.put("triggerEventId", e.eventId());
            events.append("vehicle-availability", vehicleId, "VehicleTransitionRejected", payload);
            log.warn("Transición rechazada {} -> {} por {}", from, to, e.eventType());
            return;
        }
        applyChange(current.get(), to, change, e, e.eventType());
    }

    private void applyChange(Vehicle v, VehicleState to, Change c, EventEnvelope cause, String reason) {
        int updated = jdbc.update("""
                UPDATE vehicle_state SET state = ?, zone_id = ?, latitude = ?, longitude = ?, battery_pct = ?,
                       reservation_id = ?, user_id = ?, version = version + 1, updated_at = now()
                WHERE vehicle_id = ?::uuid AND version = ?
                """, to.name(),
                c.zoneId != null ? c.zoneId : v.zoneId(),
                c.latitude != null ? c.latitude : v.latitude(),
                c.longitude != null ? c.longitude : v.longitude(),
                c.battery != null ? c.battery : v.batteryPct(),
                c.clear ? null : (c.reservationId != null ? c.reservationId : v.reservationId()),
                c.clear ? null : (c.userId != null ? c.userId : v.userId()),
                v.vehicleId(), v.version());
        if (updated == 0) {
            throw new IllegalStateException("Conflicto de versión en " + v.vehicleId() + "; se reintentará");
        }
        publishChange(get(v.vehicleId()), VehicleState.valueOf(v.state()), cause, reason);
    }

    private void publishChange(Vehicle v, VehicleState from, EventEnvelope cause, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleId", v.vehicleId());
        payload.put("code", v.code());
        payload.put("vehicleType", v.vehicleType());
        payload.put("fromState", from == null ? null : from.name());
        payload.put("toState", v.state());
        payload.put("zoneId", v.zoneId());
        payload.put("latitude", v.latitude());
        payload.put("longitude", v.longitude());
        payload.put("batteryPct", v.batteryPct());
        payload.put("reservationId", v.reservationId());
        payload.put("userId", v.userId());
        payload.put("reason", reason);
        payload.put("sourceEventId", cause.eventId());
        payload.put("sourceEventType", cause.eventType());
        events.append("vehicle-availability", v.vehicleId(), "VehicleAvailabilityChanged", payload);
    }

    private String nearestZone(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return null;
        }
        return jdbc.query("SELECT * FROM zone_ref", (rs, i) -> new ZoneRef(rs.getString("zone_id"), rs.getString("name"),
                        rs.getDouble("latitude"), rs.getDouble("longitude"))).stream()
                .min(Comparator.comparingDouble(z -> Geo.distanceKm(lat, lon, z.latitude(), z.longitude())))
                .map(ZoneRef::zoneId).orElse(null);
    }

    private Optional<ZoneRef> zone(String zoneId) {
        return jdbc.query("SELECT * FROM zone_ref WHERE zone_id = ?", (rs, i) -> new ZoneRef(rs.getString("zone_id"),
                rs.getString("name"), rs.getDouble("latitude"), rs.getDouble("longitude")), zoneId).stream().findFirst();
    }

    private static double jitter(double value) {
        return value + (Math.random() - 0.5) * 0.003;
    }

    /** Cambios a aplicar junto con la transición. */
    static final class Change {
        private Integer battery;
        private Double latitude;
        private Double longitude;
        private String zoneId;
        private String reservationId;
        private String userId;
        private boolean clear;

        static Change of() {
            return new Change();
        }

        static Change clearAssignment() {
            Change c = new Change();
            c.clear = true;
            return c;
        }

        Change battery(Integer value) {
            this.battery = value;
            return this;
        }

        Change position(Double lat, Double lon) {
            this.latitude = lat;
            this.longitude = lon;
            return this;
        }

        Change zone(String value) {
            this.zoneId = value;
            return this;
        }

        Change reservation(String reservation, String user) {
            this.reservationId = reservation;
            this.userId = user;
            return this;
        }

        Change user(String user) {
            this.userId = user;
            return this;
        }
    }
}
