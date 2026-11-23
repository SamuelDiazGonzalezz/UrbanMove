package com.urbanmove.zones;

import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.geo.Geo;
import com.urbanmove.common.web.ApiException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ZoneService implements ApplicationRunner {

    public record Zone(String zoneId, String name, double latitude, double longitude, int radiusMeters, int targetVehicles, boolean active) {
    }

    public record NewZone(String zoneId, String name, double latitude, double longitude, int radiusMeters, int targetVehicles) {
    }

    private final JdbcTemplate jdbc;
    private final EventStore events;

    public ZoneService(JdbcTemplate jdbc, EventStore events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public List<Zone> list() {
        return jdbc.query("SELECT * FROM zone WHERE active ORDER BY name", (rs, i) -> new Zone(
                rs.getString("zone_id"), rs.getString("name"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                rs.getInt("radius_meters"), rs.getInt("target_vehicles"), rs.getBoolean("active")));
    }

    public Zone get(String zoneId) {
        return list().stream().filter(z -> z.zoneId().equals(zoneId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Zona " + zoneId));
    }

    /** Zona cuyo centro está más cerca de las coordenadas. */
    public Zone locate(double latitude, double longitude) {
        return list().stream()
                .min(Comparator.comparingDouble(z -> Geo.distanceKm(latitude, longitude, z.latitude(), z.longitude())))
                .orElseThrow(() -> ApiException.notFound("Ninguna zona"));
    }

    @Transactional
    public Zone create(NewZone zone) {
        if (zone.zoneId() == null || !zone.zoneId().matches("[a-z0-9-]{2,40}")) {
            throw ApiException.badRequest("INVALID_ZONE_ID", "El id de zona usa minúsculas, números y guiones");
        }
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM zone WHERE zone_id = ?", Integer.class, zone.zoneId());
        if (exists != null && exists > 0) {
            throw ApiException.conflict("ZONE_EXISTS", "La zona " + zone.zoneId() + " ya existe");
        }
        jdbc.update("INSERT INTO zone (zone_id, name, latitude, longitude, radius_meters, target_vehicles) VALUES (?, ?, ?, ?, ?, ?)",
                zone.zoneId(), zone.name(), zone.latitude(), zone.longitude(), zone.radiusMeters(), zone.targetVehicles());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zoneId", zone.zoneId());
        payload.put("name", zone.name());
        payload.put("latitude", zone.latitude());
        payload.put("longitude", zone.longitude());
        payload.put("radiusMeters", zone.radiusMeters());
        payload.put("targetVehicles", zone.targetVehicles());
        events.append("zone", zone.zoneId(), "ZoneCreated", payload);
        return get(zone.zoneId());
    }

    /** Datos semilla: zonas de Las Palmas de Gran Canaria, publicadas como ZoneCreated. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM zone", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        create(new NewZone("vegueta", "Vegueta", 28.1010, -15.4140, 600, 3));
        create(new NewZone("triana", "Triana · San Telmo", 28.1085, -15.4175, 600, 4));
        create(new NewZone("ciudad-jardin", "Ciudad Jardín", 28.1215, -15.4265, 700, 3));
        create(new NewZone("mesa-y-lopez", "Mesa y López", 28.1300, -15.4335, 600, 4));
        create(new NewZone("las-canteras", "Las Canteras", 28.1355, -15.4385, 700, 4));
        create(new NewZone("santa-catalina", "Santa Catalina", 28.1405, -15.4300, 600, 4));
        create(new NewZone("la-isleta", "La Isleta", 28.1530, -15.4220, 800, 3));
    }
}
