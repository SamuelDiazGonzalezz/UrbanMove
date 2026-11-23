package com.urbanmove.availability;

import com.urbanmove.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService availability;

    public AvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping
    public List<AvailabilityService.Vehicle> list(@RequestParam(required = false) String zoneId,
                                                  @RequestParam(required = false) String state) {
        return availability.list(zoneId, state).stream().map(AvailabilityController::visible).toList();
    }

    @GetMapping("/summary")
    public List<Map<String, Object>> summary() {
        return availability.summary();
    }

    @GetMapping("/{vehicleId}")
    public AvailabilityService.Vehicle get(@PathVariable String vehicleId) {
        return visible(availability.get(vehicleId));
    }

    /** La consulta es pública: solo el personal ve quién tiene reservado o en uso un vehículo. */
    private static AvailabilityService.Vehicle visible(AvailabilityService.Vehicle v) {
        if (CurrentUser.isStaff()) {
            return v;
        }
        return new AvailabilityService.Vehicle(v.vehicleId(), v.code(), v.vehicleType(), v.state(), v.zoneId(),
                v.latitude(), v.longitude(), v.batteryPct(), null, null, v.version(), v.updatedAt());
    }
}
