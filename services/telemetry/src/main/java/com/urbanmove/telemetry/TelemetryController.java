package com.urbanmove.telemetry;

import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recibe lecturas de los vehículos (en el prototipo las simula la app de operaciones) y detecta
 * el cruce del umbral de batería baja: publica {@code TelemetryReceived} y, si procede,
 * {@code VehicleBatteryLow}.
 */
@RestController
@RequestMapping("/api/telemetry")
@PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
public class TelemetryController {

    static final int LOW_BATTERY_PCT = 20;

    public record Reading(String vehicleId, Integer batteryPct, Double latitude, Double longitude) {
    }

    private final JdbcTemplate jdbc;
    private final EventStore events;

    public TelemetryController(JdbcTemplate jdbc, EventStore events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public Map<String, Object> ingest(@RequestBody Reading reading) {
        UUID vehicleId;
        try {
            vehicleId = UUID.fromString(reading.vehicleId());
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_VEHICLE", "vehicleId no válido");
        }
        if (reading.batteryPct() != null && (reading.batteryPct() < 0 || reading.batteryPct() > 100)) {
            throw ApiException.badRequest("INVALID_BATTERY", "La batería va de 0 a 100");
        }
        Integer previous = jdbc.query("SELECT battery_pct FROM telemetry_reading WHERE vehicle_id = ? ORDER BY recorded_at DESC LIMIT 1",
                (rs, i) -> (Integer) rs.getObject(1), vehicleId).stream().findFirst().orElse(null);
        UUID readingId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO telemetry_reading (reading_id, vehicle_id, battery_pct, latitude, longitude, recorded_at) VALUES (?, ?, ?, ?, ?, ?)",
                readingId, vehicleId, reading.batteryPct(), reading.latitude(), reading.longitude(), Timestamp.from(now));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("readingId", readingId.toString());
        payload.put("vehicleId", reading.vehicleId());
        payload.put("batteryPct", reading.batteryPct());
        payload.put("latitude", reading.latitude());
        payload.put("longitude", reading.longitude());
        events.append("telemetry", reading.vehicleId(), "TelemetryReceived", payload);

        boolean low = reading.batteryPct() != null && reading.batteryPct() < LOW_BATTERY_PCT
                && (previous == null || previous >= LOW_BATTERY_PCT);
        if (low) {
            events.append("telemetry", reading.vehicleId(), "VehicleBatteryLow",
                    Map.of("vehicleId", reading.vehicleId(), "batteryPct", reading.batteryPct(), "thresholdPct", LOW_BATTERY_PCT));
        }
        return Map.of("readingId", readingId.toString(), "batteryLowDetected", low);
    }

    @GetMapping("/{vehicleId}")
    public List<Map<String, Object>> readings(@PathVariable String vehicleId) {
        return jdbc.queryForList("SELECT * FROM telemetry_reading WHERE vehicle_id = ?::uuid ORDER BY recorded_at DESC LIMIT 50", vehicleId);
    }
}
