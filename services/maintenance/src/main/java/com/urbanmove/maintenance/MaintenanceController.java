package com.urbanmove.maintenance;

import com.urbanmove.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class MaintenanceController {

    public record NewOrder(String vehicleId, String type, String reason) {
    }

    public record AutomaticOrder(String vehicleId, String type, String reason, String sourceEventId) {
    }

    public record Usage(String sourceEventId) {
    }

    public record ReturnRequest(String zoneId) {
    }

    private final MaintenanceService maintenance;

    public MaintenanceController(MaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @GetMapping("/api/maintenance-orders")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        return maintenance.list(status);
    }

    @PostMapping("/api/maintenance-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public Map<String, Object> create(@RequestBody NewOrder order) {
        return maintenance.open(order.vehicleId(), order.type(), order.reason() == null ? "Manual" : order.reason(), "MANUAL", null);
    }

    @PostMapping("/api/maintenance-orders/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public Map<String, Object> withdraw(@PathVariable String id) {
        return maintenance.withdraw(id, CurrentUser.username());
    }

    @PostMapping("/api/maintenance-orders/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public Map<String, Object> start(@PathVariable String id) {
        return maintenance.start(id);
    }

    @PostMapping("/api/maintenance-orders/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public Map<String, Object> complete(@PathVariable String id) {
        return maintenance.complete(id);
    }

    @PostMapping("/api/maintenance-orders/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public Map<String, Object> returnToService(@PathVariable String id, @RequestBody ReturnRequest request) {
        return maintenance.returnToService(id, request.zoneId());
    }

    @PostMapping("/api/maintenance-orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public Map<String, Object> cancel(@PathVariable String id) {
        return maintenance.cancel(id);
    }

    /** Solo red interna: la Lambda maintenance-detection abre órdenes automáticas. */
    @PostMapping("/internal/maintenance-orders")
    public Map<String, Object> automatic(@RequestBody AutomaticOrder order) {
        return maintenance.open(order.vehicleId(), order.type(), order.reason(), "AUTOMATIC", order.sourceEventId());
    }

    /** Solo red interna: la Lambda maintenance-detection notifica cada viaje registrado. */
    @PostMapping("/internal/vehicles/{vehicleId}/usage")
    public Map<String, Object> usage(@PathVariable String vehicleId, @RequestBody Usage usage) {
        return maintenance.registerUsage(vehicleId, usage.sourceEventId());
    }
}
