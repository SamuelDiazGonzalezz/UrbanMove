package com.urbanmove.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicles")
@PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public List<InventoryService.Vehicle> list() {
        return inventory.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryService.Vehicle purchase(@RequestBody InventoryService.Purchase purchase) {
        return inventory.purchase(purchase);
    }

    @PostMapping("/{vehicleId}/retire")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> retire(@PathVariable String vehicleId, @RequestBody(required = false) Map<String, String> body) {
        inventory.retire(vehicleId, body == null ? "" : body.getOrDefault("reason", ""));
        return Map.of("status", "RETIRE_REQUESTED");
    }
}
