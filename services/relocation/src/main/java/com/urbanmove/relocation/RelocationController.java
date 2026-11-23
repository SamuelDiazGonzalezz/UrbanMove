package com.urbanmove.relocation;

import com.urbanmove.common.security.CurrentUser;
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
@RequestMapping("/api/relocation-orders")
@PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
public class RelocationController {

    public record NewOrder(String fromZoneId, String toZoneId, Integer quantity, String reason) {
    }

    public record AssignRequest(String assignee) {
    }

    public record CompleteRequest(List<String> vehicleIds) {
    }

    private final RelocationService relocation;

    public RelocationController(RelocationService relocation) {
        this.relocation = relocation;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return relocation.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody NewOrder order) {
        return relocation.create(order.fromZoneId(), order.toZoneId(), order.quantity() == null ? 1 : order.quantity(),
                "MANUAL", order.reason() == null ? "Manual" : order.reason(), null);
    }

    @PostMapping("/{id}/assign")
    public Map<String, Object> assign(@PathVariable String id, @RequestBody(required = false) AssignRequest request) {
        String assignee = request == null || request.assignee() == null || request.assignee().isBlank()
                ? CurrentUser.username() : request.assignee();
        return relocation.assign(id, assignee);
    }

    @PostMapping("/{id}/start")
    public Map<String, Object> start(@PathVariable String id) {
        return relocation.start(id);
    }

    @PostMapping("/{id}/complete")
    public Map<String, Object> complete(@PathVariable String id, @RequestBody CompleteRequest request) {
        return relocation.complete(id, request.vehicleIds());
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        return relocation.cancel(id);
    }
}
