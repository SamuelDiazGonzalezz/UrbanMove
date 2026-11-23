package com.urbanmove.inventory;

import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class InventoryService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    public record Vehicle(String vehicleId, String code, String vehicleType, String model, String homeZoneId,
                         Instant purchasedAt, Instant retiredAt, String retireReason) {
    }

    public record Purchase(String code, String vehicleType, String model, String zoneId) {
    }

    record ZoneDto(String zoneId, String name, double latitude, double longitude) {
    }

    private final JdbcTemplate jdbc;
    private final EventStore events;
    private final RestClient zonesClient;
    private final TransactionTemplate tx;
    private final Random random = new Random(42);

    public InventoryService(JdbcTemplate jdbc, EventStore events, RestClient.Builder builder, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.events = events;
        this.zonesClient = builder.baseUrl("http://zones:8080").build();
        this.tx = tx;
    }

    public List<Vehicle> list() {
        return jdbc.query("SELECT * FROM vehicle_asset ORDER BY code", (rs, i) -> new Vehicle(
                rs.getString("vehicle_id"), rs.getString("code"), rs.getString("vehicle_type"), rs.getString("model"),
                rs.getString("home_zone_id"), rs.getTimestamp("purchased_at").toInstant(),
                rs.getTimestamp("retired_at") == null ? null : rs.getTimestamp("retired_at").toInstant(),
                rs.getString("retire_reason")));
    }

    @Transactional
    public Vehicle purchase(Purchase purchase) {
        if (purchase.vehicleType() == null || !List.of("BICYCLE", "ELECTRIC_BICYCLE", "ELECTRIC_SCOOTER").contains(purchase.vehicleType())) {
            throw ApiException.badRequest("INVALID_TYPE", "Tipo de vehículo no válido");
        }
        ZoneDto zone = zone(purchase.zoneId());
        UUID id = UUID.randomUUID();
        String code = purchase.code() == null || purchase.code().isBlank() ? nextCode(purchase.vehicleType()) : purchase.code().trim().toUpperCase();
        String model = purchase.model() == null || purchase.model().isBlank() ? defaultModel(purchase.vehicleType()) : purchase.model();
        jdbc.update("INSERT INTO vehicle_asset (vehicle_id, code, vehicle_type, model, home_zone_id) VALUES (?, ?, ?, ?, ?)",
                id, code, purchase.vehicleType(), model, zone.zoneId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleId", id.toString());
        payload.put("code", code);
        payload.put("vehicleType", purchase.vehicleType());
        payload.put("model", model);
        payload.put("zoneId", zone.zoneId());
        payload.put("latitude", jitter(zone.latitude()));
        payload.put("longitude", jitter(zone.longitude()));
        payload.put("batteryPct", purchase.vehicleType().equals("BICYCLE") ? null : 100);
        events.append("vehicle", id.toString(), "VehiclePurchased", payload);
        return list().stream().filter(v -> v.vehicleId().equals(id.toString())).findFirst().orElseThrow();
    }

    @Transactional
    public void retire(String vehicleId, String reason) {
        int updated = jdbc.update("UPDATE vehicle_asset SET retired_at = now(), retire_reason = ? WHERE vehicle_id = ?::uuid AND retired_at IS NULL",
                reason, vehicleId);
        if (updated == 0) {
            throw ApiException.conflict("NOT_RETIRABLE", "El vehículo no existe o ya está dado de baja");
        }
        events.append("vehicle", vehicleId, "VehicleRetired", Map.of("vehicleId", vehicleId, "reason", reason == null ? "" : reason));
    }

    private ZoneDto zone(String zoneId) {
        try {
            return zonesClient.get().uri("/api/zones/{id}", zoneId).retrieve().body(ZoneDto.class);
        } catch (Exception e) {
            throw ApiException.badRequest("UNKNOWN_ZONE", "Zona desconocida o servicio de zonas no disponible: " + zoneId);
        }
    }

    private String nextCode(String type) {
        String prefix = switch (type) {
            case "ELECTRIC_SCOOTER" -> "PAT";
            case "ELECTRIC_BICYCLE" -> "EBK";
            default -> "BIC";
        };
        Integer n = jdbc.queryForObject("SELECT count(*) FROM vehicle_asset WHERE code LIKE ?", Integer.class, prefix + "-%");
        return "%s-%03d".formatted(prefix, (n == null ? 0 : n) + 1);
    }

    private static String defaultModel(String type) {
        return switch (type) {
            case "ELECTRIC_SCOOTER" -> "Ninebot Max G30";
            case "ELECTRIC_BICYCLE" -> "Orbea Vibe H30";
            default -> "BH Urban 700";
        };
    }

    private double jitter(double value) {
        return value + (random.nextDouble() - 0.5) * 0.004;
    }

    /** Datos semilla: flota inicial repartida por zonas, publicada como VehiclePurchased. */
    @Override
    public void run(ApplicationArguments args) {
        Thread seeder = new Thread(this::seedWhenZonesReady, "inventory-seed");
        seeder.setDaemon(true);
        seeder.start();
    }

    private void seedWhenZonesReady() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM vehicle_asset", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        for (int attempt = 0; attempt < 60; attempt++) {
            try {
                List<ZoneDto> zones = zonesClient.get().uri("/api/zones").retrieve().body(new ParameterizedTypeReference<>() {
                });
                if (zones != null && !zones.isEmpty()) {
                    tx.executeWithoutResult(s -> {
                        String[] types = {"ELECTRIC_SCOOTER", "ELECTRIC_BICYCLE", "BICYCLE"};
                        int i = 0;
                        for (ZoneDto zone : zones) {
                            for (int k = 0; k < 3; k++) {
                                purchase(new Purchase(null, types[i++ % types.length], null, zone.zoneId()));
                            }
                        }
                    });
                    log.info("Flota semilla creada en {} zonas", zones.size());
                    return;
                }
            } catch (Exception e) {
                log.debug("Zonas aún no disponibles: {}", e.getMessage());
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("No se pudo crear la flota semilla: el servicio de zonas no respondió");
    }
}
