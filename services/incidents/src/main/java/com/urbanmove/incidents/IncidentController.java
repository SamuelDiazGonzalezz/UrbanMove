package com.urbanmove.incidents;

import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.security.CurrentUser;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    public record Report(String vehicleId, String type, String description) {
    }

    public record Resolve(String resolution) {
    }

    private final JdbcTemplate jdbc;
    private final EventStore events;

    public IncidentController(JdbcTemplate jdbc, EventStore events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> report(@RequestBody Report report) {
        if (report.type() == null || !Set.of("DAMAGE", "BATTERY", "PARKING", "OTHER").contains(report.type())) {
            throw ApiException.badRequest("INVALID_TYPE", "Tipo de incidencia no válido");
        }
        UUID vehicleId;
        try {
            vehicleId = UUID.fromString(report.vehicleId());
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_VEHICLE", "vehicleId no válido");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO incident (incident_id, vehicle_id, reported_by, type, description, status) VALUES (?, ?, ?, ?, ?, 'OPEN')",
                id, vehicleId, CurrentUser.id(), report.type(), report.description());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incidentId", id.toString());
        payload.put("vehicleId", report.vehicleId());
        payload.put("userId", CurrentUser.id());
        payload.put("type", report.type());
        payload.put("description", report.description());
        events.append("incident", id.toString(), "IncidentReported", payload);
        return get(id.toString());
    }

    @GetMapping("/me")
    public List<Map<String, Object>> mine() {
        return jdbc.queryForList("SELECT * FROM incident WHERE reported_by = ? ORDER BY created_at DESC LIMIT 50", CurrentUser.id());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT * FROM incident ORDER BY status, created_at DESC LIMIT 200");
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    @Transactional
    public Map<String, Object> resolve(@PathVariable String id, @RequestBody(required = false) Resolve body) {
        String resolution = body == null || body.resolution() == null ? "" : body.resolution();
        int updated = jdbc.update("UPDATE incident SET status = 'RESOLVED', resolved_at = now(), resolution = ? WHERE incident_id = ?::uuid AND status = 'OPEN'",
                resolution, id);
        if (updated == 0) {
            throw ApiException.conflict("NOT_OPEN", "La incidencia no existe o ya está resuelta");
        }
        Map<String, Object> incident = get(id);
        events.append("incident", id, "IncidentResolved",
                Map.of("incidentId", id, "vehicleId", incident.get("vehicle_id").toString(), "resolution", resolution));
        return incident;
    }

    private Map<String, Object> get(String id) {
        return jdbc.queryForList("SELECT * FROM incident WHERE incident_id = ?::uuid", id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Incidencia " + id));
    }
}
