package com.urbanmove.rides;

import com.urbanmove.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    public record StartRequest(String vehicleId) {
    }

    public record FinishRequest(String destinationZoneId) {
    }

    private final RideService rides;

    public RideController(RideService rides) {
        this.rides = rides;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public RideService.Trip start(@RequestBody StartRequest request) {
        return rides.start(request.vehicleId(), CurrentUser.id());
    }

    @PostMapping("/{tripId}/finish")
    public RideService.Trip finish(@PathVariable String tripId, @RequestBody FinishRequest request) {
        return rides.finish(tripId, CurrentUser.id(), CurrentUser.isStaff(), request.destinationZoneId());
    }

    @GetMapping("/me")
    public List<RideService.Trip> mine() {
        return rides.byUser(CurrentUser.id());
    }

    @GetMapping("/me/active")
    public ResponseEntity<RideService.Trip> active() {
        return rides.active(CurrentUser.id()).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public List<RideService.Trip> recent() {
        return rides.recent();
    }
}
