package com.urbanmove.reservations;

import com.urbanmove.common.events.EventEnvelope;
import com.urbanmove.common.events.EventHandler;
import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ReservationService implements EventHandler {

    public record Reservation(String reservationId, String vehicleId, String vehicleCode, String userId, String zoneId,
                              String status, Instant createdAt, Instant expiresAt, Instant endedAt, String endReason) {
    }

    record VehicleView(String vehicleId, String code, String vehicleType, String state, String zoneId, Integer batteryPct) {
    }

    private static final RowMapper<Reservation> MAPPER = (rs, i) -> new Reservation(
            rs.getString("reservation_id"), rs.getString("vehicle_id"), rs.getString("vehicle_code"), rs.getString("user_id"),
            rs.getString("zone_id"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant(), rs.getString("end_reason"));

    private final JdbcTemplate jdbc;
    private final EventStore events;
    private final TransactionTemplate tx;
    private final int ttlSeconds;

    public ReservationService(JdbcTemplate jdbc, EventStore events, TransactionTemplate tx,
                              @Value("${urbanmove.reservations.ttl-seconds:${RESERVATION_TTL_SECONDS:300}}") int ttlSeconds) {
        this.jdbc = jdbc;
        this.events = events;
        this.tx = tx;
        this.ttlSeconds = ttlSeconds;
    }

    // ---------------------------------------------------------------- comandos

    public Reservation reserve(String vehicleId, String userId) {
        Optional<VehicleView> vehicle = vehicle(vehicleId);
        String rejection;
        if (vehicle.isEmpty()) {
            rejection = "VEHICLE_UNKNOWN";
        } else if (!"AVAILABLE".equals(vehicle.get().state())) {
            rejection = "VEHICLE_NOT_AVAILABLE";
        } else {
            try {
                return tx.execute(s -> create(vehicle.get(), userId));
            } catch (DuplicateKeyException e) {
                rejection = hasActive(userId) ? "USER_HAS_ACTIVE_RESERVATION" : "VEHICLE_ALREADY_RESERVED";
            }
        }
        String reason = rejection;
        tx.executeWithoutResult(s -> recordRejection(vehicleId, userId, vehicle.map(VehicleView::zoneId).orElse(null), reason));
        throw ApiException.conflict(reason, switch (reason) {
            case "USER_HAS_ACTIVE_RESERVATION" -> "Ya tienes una reserva activa";
            case "VEHICLE_UNKNOWN" -> "El vehículo no existe";
            default -> "El vehículo ya no está disponible";
        });
    }

    private Reservation create(VehicleView vehicle, String userId) {
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        jdbc.update("""
                INSERT INTO reservation (reservation_id, vehicle_id, vehicle_code, user_id, zone_id, status, expires_at)
                VALUES (?, ?::uuid, ?, ?, ?, 'ACTIVE', ?)
                """, id, vehicle.vehicleId(), vehicle.code(), userId, vehicle.zoneId(), Timestamp.from(expiresAt));
        jdbc.update("INSERT INTO reservation_attempt (attempt_id, vehicle_id, user_id, zone_id, result) VALUES (?, ?::uuid, ?, ?, 'ACCEPTED')",
                UUID.randomUUID(), vehicle.vehicleId(), userId, vehicle.zoneId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", id.toString());
        payload.put("vehicleId", vehicle.vehicleId());
        payload.put("userId", userId);
        payload.put("zoneId", vehicle.zoneId());
        payload.put("vehicleType", vehicle.vehicleType());
        payload.put("expiresAt", expiresAt.toString());
        payload.put("ttlSeconds", ttlSeconds);
        events.append("reservation", id.toString(), "VehicleReserved", payload);
        return get(id.toString());
    }

    private void recordRejection(String vehicleId, String userId, String zoneId, String reason) {
        UUID vehicleUuid = parse(vehicleId);
        jdbc.update("INSERT INTO reservation_attempt (attempt_id, vehicle_id, user_id, zone_id, result, reason) VALUES (?, ?, ?, ?, 'REJECTED', ?)",
                UUID.randomUUID(), vehicleUuid, userId, zoneId, reason);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleId", vehicleId);
        payload.put("userId", userId);
        payload.put("zoneId", zoneId);
        payload.put("reason", reason);
        events.append("reservation-attempt", userId, "ReservationRejected", payload);
    }

    @Transactional
    public Reservation cancel(String reservationId, String userId, boolean staff) {
        Reservation r = get(reservationId);
        if (!staff && !r.userId().equals(userId)) {
            throw ApiException.forbidden("La reserva no es tuya");
        }
        if (!end(r, "CANCELLED", "USER")) {
            throw ApiException.conflict("NOT_ACTIVE", "La reserva ya no está activa");
        }
        return get(reservationId);
    }

    /** Llamado por la Lambda reservation-expiration cuando vence el retraso de la cola. */
    @Transactional
    public String expire(String reservationId) {
        Optional<Reservation> r = find(reservationId);
        if (r.isEmpty()) {
            return "UNKNOWN";
        }
        if (!r.get().status().equals("ACTIVE")) {
            return "ALREADY_" + r.get().status();
        }
        if (r.get().expiresAt().isAfter(Instant.now().plusSeconds(5))) {
            return "NOT_DUE";
        }
        end(r.get(), "EXPIRED", "TIMEOUT");
        return "EXPIRED";
    }

    private boolean end(Reservation r, String status, String reason) {
        int updated = jdbc.update("UPDATE reservation SET status = ?, ended_at = now(), end_reason = ? WHERE reservation_id = ?::uuid AND status = 'ACTIVE'",
                status, reason, r.reservationId());
        if (updated == 0) {
            return false;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", r.reservationId());
        payload.put("vehicleId", r.vehicleId());
        payload.put("userId", r.userId());
        payload.put("zoneId", r.zoneId());
        payload.put("reason", reason);
        String type = switch (status) {
            case "EXPIRED" -> "ReservationExpired";
            case "COMPLETED" -> "ReservationCompleted";
            default -> "ReservationCancelled";
        };
        events.append("reservation", r.reservationId(), type, payload);
        return true;
    }

    // ---------------------------------------------------------------- consultas

    public List<Reservation> byUser(String userId) {
        return jdbc.query("SELECT * FROM reservation WHERE user_id = ? ORDER BY created_at DESC LIMIT 50", MAPPER, userId);
    }

    public List<Reservation> recent() {
        return jdbc.query("SELECT * FROM reservation ORDER BY created_at DESC LIMIT 200", MAPPER);
    }

    public Reservation get(String reservationId) {
        return find(reservationId).orElseThrow(() -> ApiException.notFound("Reserva " + reservationId));
    }

    private Optional<Reservation> find(String reservationId) {
        if (parse(reservationId) == null) {
            return Optional.empty();
        }
        return jdbc.query("SELECT * FROM reservation WHERE reservation_id = ?::uuid", MAPPER, reservationId).stream().findFirst();
    }

    private boolean hasActive(String userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM reservation WHERE user_id = ? AND status = 'ACTIVE'", Integer.class, userId);
        return n != null && n > 0;
    }

    private Optional<VehicleView> vehicle(String vehicleId) {
        if (parse(vehicleId) == null) {
            return Optional.empty();
        }
        return jdbc.query("SELECT * FROM vehicle_view WHERE vehicle_id = ?::uuid", (rs, i) -> new VehicleView(
                rs.getString("vehicle_id"), rs.getString("code"), rs.getString("vehicle_type"), rs.getString("state"),
                rs.getString("zone_id"), (Integer) rs.getObject("battery_pct")), vehicleId).stream().findFirst();
    }

    private static UUID parse(String id) {
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- eventos

    @Override
    public Set<String> eventTypes() {
        return Set.of("VehicleAvailabilityChanged", "VehicleReservationRejected", "VehiclePickedUp");
    }

    @Override
    public void handle(EventEnvelope e) {
        switch (e.eventType()) {
            case "VehicleAvailabilityChanged" -> {
                jdbc.update("""
                        INSERT INTO vehicle_view (vehicle_id, code, vehicle_type, state, zone_id, battery_pct, reservation_id, updated_at)
                        VALUES (?::uuid, ?, ?, ?, ?, ?, ?, now())
                        ON CONFLICT (vehicle_id) DO UPDATE SET state = EXCLUDED.state, zone_id = EXCLUDED.zone_id,
                            battery_pct = EXCLUDED.battery_pct, reservation_id = EXCLUDED.reservation_id, updated_at = now()
                        """, e.text("vehicleId"), e.text("code"), e.text("vehicleType"), e.text("toState"),
                        e.text("zoneId"), e.integer("batteryPct"), e.text("reservationId"));
                String to = e.text("toState");
                if ("WITHDRAWN".equals(to) || "RETIRED".equals(to)) {
                    activeForVehicle(e.text("vehicleId")).ifPresent(r -> end(r, "CANCELLED", "VEHICLE_WITHDRAWN"));
                }
            }
            case "VehicleReservationRejected" -> find(e.text("reservationId"))
                    .ifPresent(r -> end(r, "CANCELLED", "VEHICLE_UNAVAILABLE"));
            case "VehiclePickedUp" -> activeForVehicle(e.text("vehicleId"))
                    .filter(r -> r.userId().equals(e.text("userId")))
                    .ifPresent(r -> end(r, "COMPLETED", "RIDE_STARTED"));
            default -> {
            }
        }
    }

    private Optional<Reservation> activeForVehicle(String vehicleId) {
        return jdbc.query("SELECT * FROM reservation WHERE vehicle_id = ?::uuid AND status = 'ACTIVE'", MAPPER, vehicleId)
                .stream().findFirst();
    }
}
