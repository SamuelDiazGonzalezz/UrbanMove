package com.urbanmove.rides;

import com.urbanmove.common.events.EventEnvelope;
import com.urbanmove.common.events.EventHandler;
import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.geo.Geo;
import com.urbanmove.common.web.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class RideService implements EventHandler {

    static final BigDecimal UNLOCK_FEE = new BigDecimal("0.50");
    static final BigDecimal PER_MINUTE = new BigDecimal("0.15");
    static final BigDecimal PER_KM = new BigDecimal("0.25");
    static final double ROUTE_FACTOR = 1.3;

    public record Trip(String tripId, String vehicleId, String vehicleCode, String vehicleType, String userId,
                       String reservationId, String status, String originZoneId, Double originLatitude,
                       Double originLongitude, Instant startedAt, Integer startBatteryPct, String destinationZoneId,
                       Double destLatitude, Double destLongitude, Instant finishedAt, Integer endBatteryPct,
                       Integer durationSeconds, Integer distanceMeters, BigDecimal cost) {
    }

    record VehicleView(String vehicleId, String code, String vehicleType, String state, String zoneId,
                       Double latitude, Double longitude, Integer batteryPct, String reservationId, String userId) {
    }

    record ZoneRef(String zoneId, String name, double latitude, double longitude) {
    }

    private static final RowMapper<Trip> MAPPER = (rs, i) -> new Trip(
            rs.getString("trip_id"), rs.getString("vehicle_id"), rs.getString("vehicle_code"), rs.getString("vehicle_type"),
            rs.getString("user_id"), rs.getString("reservation_id"), rs.getString("status"), rs.getString("origin_zone_id"),
            (Double) rs.getObject("origin_latitude"), (Double) rs.getObject("origin_longitude"),
            rs.getTimestamp("started_at").toInstant(), (Integer) rs.getObject("start_battery_pct"),
            rs.getString("destination_zone_id"), (Double) rs.getObject("dest_latitude"), (Double) rs.getObject("dest_longitude"),
            rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
            (Integer) rs.getObject("end_battery_pct"), (Integer) rs.getObject("duration_seconds"),
            (Integer) rs.getObject("distance_meters"), rs.getBigDecimal("cost"));

    private final JdbcTemplate jdbc;
    private final EventStore events;

    public RideService(JdbcTemplate jdbc, EventStore events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    // ---------------------------------------------------------------- comandos

    @Transactional
    public Trip start(String vehicleId, String userId) {
        VehicleView v = vehicle(vehicleId).orElseThrow(() -> ApiException.notFound("Vehículo " + vehicleId));
        boolean reservedByMe = "RESERVED".equals(v.state()) && userId.equals(v.userId());
        if (!"AVAILABLE".equals(v.state()) && !reservedByMe) {
            throw ApiException.conflict("VEHICLE_NOT_AVAILABLE", "Este vehículo no está disponible para iniciar un viaje");
        }
        UUID tripId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("""
                    INSERT INTO trip (trip_id, vehicle_id, vehicle_code, vehicle_type, user_id, reservation_id, status,
                        origin_zone_id, origin_latitude, origin_longitude, started_at, start_battery_pct)
                    VALUES (?, ?::uuid, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?)
                    """, tripId, v.vehicleId(), v.code(), v.vehicleType(), userId, reservedByMe ? v.reservationId() : null,
                    v.zoneId(), v.latitude(), v.longitude(), Timestamp.from(now), v.batteryPct());
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("TRIP_ALREADY_OPEN", "Ya tienes un viaje en curso o el vehículo está en uso");
        }
        // RAW: hecho físico sin tripId, zona, duración ni coste (EVENTS.md §2.1).
        Map<String, Object> pickup = new LinkedHashMap<>();
        pickup.put("vehicleId", v.vehicleId());
        pickup.put("userId", userId);
        pickup.put("latitude", v.latitude());
        pickup.put("longitude", v.longitude());
        pickup.put("batteryPct", v.batteryPct());
        events.append("vehicle-ride", v.vehicleId(), "VehiclePickedUp", pickup);
        return get(tripId.toString());
    }

    @Transactional
    public Trip finish(String tripId, String userId, boolean staff, String destinationZoneId) {
        Trip trip = get(tripId);
        if (!staff && !trip.userId().equals(userId)) {
            throw ApiException.forbidden("El viaje no es tuyo");
        }
        if (!"OPEN".equals(trip.status())) {
            throw ApiException.conflict("TRIP_NOT_OPEN", "El viaje ya está finalizado");
        }
        ZoneRef zone = zone(destinationZoneId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_ZONE", "Indica una zona de destino válida"));
        double destLat = zone.latitude() + (Math.random() - 0.5) * 0.003;
        double destLon = zone.longitude() + (Math.random() - 0.5) * 0.003;
        Instant now = Instant.now();
        int duration = (int) Math.max(1, Duration.between(trip.startedAt(), now).toSeconds());
        double km = trip.originLatitude() == null ? 0 : Geo.distanceKm(trip.originLatitude(), trip.originLongitude(), destLat, destLon) * ROUTE_FACTOR;
        int meters = (int) Math.round(km * 1000);
        Integer endBattery = trip.startBatteryPct() == null ? null
                : Math.max(0, trip.startBatteryPct() - (int) Math.ceil(km * 4 + duration / 120.0));
        BigDecimal minutes = BigDecimal.valueOf(Math.max(1, Math.ceil(duration / 60.0)));
        BigDecimal cost = UNLOCK_FEE.add(PER_MINUTE.multiply(minutes)).add(PER_KM.multiply(BigDecimal.valueOf(km)))
                .setScale(2, RoundingMode.HALF_UP);

        jdbc.update("""
                UPDATE trip SET status = 'COMPLETED', destination_zone_id = ?, dest_latitude = ?, dest_longitude = ?,
                    finished_at = ?, end_battery_pct = ?, duration_seconds = ?, distance_meters = ?, cost = ?
                WHERE trip_id = ?::uuid AND status = 'OPEN'
                """, zone.zoneId(), destLat, destLon, Timestamp.from(now), endBattery, duration, meters, cost, tripId);

        Map<String, Object> dropoff = new LinkedHashMap<>();
        dropoff.put("vehicleId", trip.vehicleId());
        dropoff.put("userId", trip.userId());
        dropoff.put("latitude", destLat);
        dropoff.put("longitude", destLon);
        dropoff.put("batteryPct", endBattery);
        events.append("vehicle-ride", trip.vehicleId(), "VehicleDroppedOff", dropoff);

        // TRUSTED: el viaje correlacionado (pickup + dropoff), con zonas, duración y coste.
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("zoneId", trip.originZoneId());
        origin.put("latitude", trip.originLatitude());
        origin.put("longitude", trip.originLongitude());
        origin.put("occurredAt", trip.startedAt().toString());
        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("zoneId", zone.zoneId());
        destination.put("latitude", destLat);
        destination.put("longitude", destLon);
        destination.put("occurredAt", now.toString());
        Map<String, Object> recorded = new LinkedHashMap<>();
        recorded.put("tripId", tripId);
        recorded.put("vehicleId", trip.vehicleId());
        recorded.put("vehicleType", trip.vehicleType());
        recorded.put("userId", trip.userId());
        recorded.put("reservationId", trip.reservationId());
        recorded.put("origin", origin);
        recorded.put("destination", destination);
        recorded.put("durationSeconds", duration);
        recorded.put("distanceMeters", meters);
        recorded.put("batteryDropPct", endBattery == null ? null : trip.startBatteryPct() - endBattery);
        recorded.put("preliminaryCost", cost);
        recorded.put("status", "COMPLETED");
        events.append("trip", tripId, "TripRecorded", recorded);
        return get(tripId);
    }

    // ---------------------------------------------------------------- consultas

    public Trip get(String tripId) {
        return jdbc.query("SELECT * FROM trip WHERE trip_id = ?::uuid", MAPPER, tripId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Viaje " + tripId));
    }

    public List<Trip> byUser(String userId) {
        return jdbc.query("SELECT * FROM trip WHERE user_id = ? ORDER BY started_at DESC LIMIT 50", MAPPER, userId);
    }

    public Optional<Trip> active(String userId) {
        return jdbc.query("SELECT * FROM trip WHERE user_id = ? AND status = 'OPEN'", MAPPER, userId).stream().findFirst();
    }

    public List<Trip> recent() {
        return jdbc.query("SELECT * FROM trip ORDER BY started_at DESC LIMIT 200", MAPPER);
    }

    private Optional<VehicleView> vehicle(String vehicleId) {
        try {
            UUID.fromString(vehicleId);
        } catch (Exception e) {
            return Optional.empty();
        }
        return jdbc.query("SELECT * FROM vehicle_view WHERE vehicle_id = ?::uuid", (rs, i) -> new VehicleView(
                rs.getString("vehicle_id"), rs.getString("code"), rs.getString("vehicle_type"), rs.getString("state"),
                rs.getString("zone_id"), (Double) rs.getObject("latitude"), (Double) rs.getObject("longitude"),
                (Integer) rs.getObject("battery_pct"), rs.getString("reservation_id"), rs.getString("user_id")), vehicleId)
                .stream().findFirst();
    }

    private Optional<ZoneRef> zone(String zoneId) {
        return jdbc.query("SELECT * FROM zone_ref WHERE zone_id = ?", (rs, i) -> new ZoneRef(rs.getString("zone_id"),
                rs.getString("name"), rs.getDouble("latitude"), rs.getDouble("longitude")), zoneId).stream().findFirst();
    }

    // ---------------------------------------------------------------- eventos

    @Override
    public Set<String> eventTypes() {
        return Set.of("ZoneCreated", "VehicleAvailabilityChanged");
    }

    @Override
    public void handle(EventEnvelope e) {
        if (e.eventType().equals("ZoneCreated")) {
            jdbc.update("""
                    INSERT INTO zone_ref (zone_id, name, latitude, longitude) VALUES (?, ?, ?, ?)
                    ON CONFLICT (zone_id) DO UPDATE SET name = EXCLUDED.name, latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude
                    """, e.text("zoneId"), e.text("name"), e.decimal("latitude"), e.decimal("longitude"));
            return;
        }
        jdbc.update("""
                INSERT INTO vehicle_view (vehicle_id, code, vehicle_type, state, zone_id, latitude, longitude, battery_pct,
                    reservation_id, user_id, updated_at)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (vehicle_id) DO UPDATE SET state = EXCLUDED.state, zone_id = EXCLUDED.zone_id,
                    latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude, battery_pct = EXCLUDED.battery_pct,
                    reservation_id = EXCLUDED.reservation_id, user_id = EXCLUDED.user_id, updated_at = now()
                """, e.text("vehicleId"), e.text("code"), e.text("vehicleType"), e.text("toState"), e.text("zoneId"),
                e.decimal("latitude"), e.decimal("longitude"), e.integer("batteryPct"), e.text("reservationId"), e.text("userId"));
    }
}
