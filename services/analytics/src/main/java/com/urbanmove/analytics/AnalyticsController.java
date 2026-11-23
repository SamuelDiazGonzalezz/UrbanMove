package com.urbanmove.analytics;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API del dashboard: lee las vistas {@code mart_*_current} de analytics_db (escritas por la
 * Lambda mart-builder) y añade la salud de los servicios y el estado de las DLQ.
 */
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
public class AnalyticsController {

    static final List<String> SERVICES = List.of("identity", "user-profiles", "zones", "inventory", "availability",
            "telemetry", "reservations", "rides", "incidents", "maintenance", "relocation", "history", "analytics");

    private final JdbcTemplate jdbc;
    private final SqsClient sqs;
    private final RestClient http;

    public AnalyticsController(JdbcTemplate jdbc, SqsClient sqs) {
        this.jdbc = jdbc;
        this.sqs = sqs;
        this.http = RestClient.builder().requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(800)).withReadTimeout(Duration.ofMillis(1500)))).build();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("build", jdbc.queryForList("SELECT build_id, built_at, facts_count, duration_ms FROM mart_build_current").stream().findFirst().orElse(null));
        out.put("fleetByState", jdbc.queryForList("SELECT state, sum(vehicles) AS vehicles FROM mart_flota_current GROUP BY state ORDER BY state"));
        out.put("fleetByZone", jdbc.queryForList("""
                SELECT zone_id,
                       sum(vehicles) FILTER (WHERE state = 'AVAILABLE') AS available,
                       sum(vehicles) FILTER (WHERE state = 'RESERVED') AS reserved,
                       sum(vehicles) FILTER (WHERE state = 'IN_USE') AS in_use,
                       sum(vehicles) FILTER (WHERE state = 'LOW_BATTERY') AS low_battery,
                       sum(vehicles) FILTER (WHERE state IN ('WITHDRAWN','CHARGING','REPAIRING','READY_FOR_SERVICE')) AS maintenance
                FROM mart_flota_current WHERE state <> 'RETIRED' GROUP BY zone_id ORDER BY zone_id
                """));
        out.put("reservationsToday", jdbc.queryForList("""
                SELECT coalesce(sum(reserved),0) AS reserved, coalesce(sum(cancelled),0) AS cancelled,
                       coalesce(sum(expired),0) AS expired, coalesce(sum(completed),0) AS completed, coalesce(sum(rejected),0) AS rejected
                FROM mart_reservas_current WHERE date_key = current_date
                """).get(0));
        out.put("reservationsByHour", jdbc.queryForList("""
                SELECT hour, sum(reserved) AS reserved, sum(cancelled) AS cancelled, sum(expired) AS expired
                FROM mart_reservas_current WHERE date_key = current_date GROUP BY hour ORDER BY hour
                """));
        out.put("tripsToday", jdbc.queryForList("""
                SELECT coalesce(sum(trips),0) AS trips, coalesce(sum(revenue),0) AS revenue,
                       coalesce(sum(total_distance_meters),0) AS distance_meters,
                       coalesce(round(sum(avg_duration_seconds * trips) / nullif(sum(trips),0), 1), 0) AS avg_duration_seconds
                FROM mart_viajes_current WHERE date_key = current_date
                """).get(0));
        out.put("tripsByHour", jdbc.queryForList("""
                SELECT hour, sum(trips) AS trips, sum(revenue) AS revenue FROM mart_viajes_current
                WHERE date_key = current_date GROUP BY hour ORDER BY hour
                """));
        out.put("topRoutes", jdbc.queryForList("""
                SELECT origin_zone_id, destination_zone_id, sum(trips) AS trips FROM mart_viajes_current
                GROUP BY origin_zone_id, destination_zone_id ORDER BY trips DESC LIMIT 6
                """));
        out.put("demand", jdbc.queryForList("SELECT * FROM mart_demanda_current ORDER BY demand_last_hour DESC, zone_id"));
        out.put("maintenance", jdbc.queryForList("SELECT * FROM mart_mantenimiento_current ORDER BY date_key DESC LIMIT 7"));
        out.put("relocation", jdbc.queryForList("""
                SELECT coalesce(sum(orders_created),0) AS created, coalesce(sum(orders_completed),0) AS completed,
                       coalesce(sum(vehicles_moved),0) AS vehicles_moved,
                       round(avg(avg_minutes_to_complete), 1) AS avg_minutes
                FROM mart_operaciones_current
                """).get(0));
        out.put("users", jdbc.queryForList("SELECT * FROM mart_usuarios_current ORDER BY date_key DESC LIMIT 7"));
        out.put("eventsLastHour", jdbc.queryForList("""
                SELECT event_type, count(*) AS total FROM fact_event
                WHERE occurred_at > now() - interval '1 hour' GROUP BY event_type ORDER BY total DESC
                """));
        return out;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        List<CompletableFuture<Map<String, Object>>> checks = new ArrayList<>();
        for (String service : SERVICES) {
            checks.add(CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                String status;
                try {
                    Map<?, ?> body = http.get().uri("http://" + service + ":8080/actuator/health").retrieve().body(Map.class);
                    status = body == null ? "UNKNOWN" : String.valueOf(body.get("status"));
                } catch (Exception e) {
                    status = "DOWN";
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("service", service);
                row.put("status", status);
                row.put("latencyMs", System.currentTimeMillis() - start);
                return row;
            }));
        }
        List<Map<String, Object>> services = checks.stream().map(CompletableFuture::join).toList();

        List<Map<String, Object>> queues = new ArrayList<>();
        try {
            for (String url : sqs.listQueues().queueUrls()) {
                Map<QueueAttributeName, String> attrs = sqs.getQueueAttributes(b -> b.queueUrl(url)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)).attributes();
                Map<String, Object> row = new LinkedHashMap<>();
                String name = url.substring(url.lastIndexOf('/') + 1);
                row.put("queue", name);
                row.put("dlq", name.endsWith("-dlq"));
                row.put("messages", Integer.parseInt(attrs.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0")));
                queues.add(row);
            }
        } catch (Exception e) {
            queues.add(Map.of("queue", "sqs", "dlq", false, "messages", -1, "error", String.valueOf(e.getMessage())));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("services", services);
        out.put("queues", queues);
        out.put("failedEvents", queues.stream().filter(q -> Boolean.TRUE.equals(q.get("dlq")))
                .mapToInt(q -> (Integer) q.get("messages")).filter(n -> n > 0).sum());
        return out;
    }
}
