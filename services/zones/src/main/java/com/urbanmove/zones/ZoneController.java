package com.urbanmove.zones;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneService zones;

    public ZoneController(ZoneService zones) {
        this.zones = zones;
    }

    @GetMapping
    public List<ZoneService.Zone> list() {
        return zones.list();
    }

    @GetMapping("/locate")
    public ZoneService.Zone locate(@RequestParam double lat, @RequestParam double lon) {
        return zones.locate(lat, lon);
    }

    @GetMapping("/{zoneId}")
    public ZoneService.Zone get(@PathVariable String zoneId) {
        return zones.get(zoneId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneService.Zone create(@RequestBody ZoneService.NewZone zone) {
        return zones.create(zone);
    }
}
