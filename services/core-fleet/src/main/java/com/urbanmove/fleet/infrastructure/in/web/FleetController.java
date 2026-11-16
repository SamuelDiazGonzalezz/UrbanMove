package com.urbanmove.fleet.infrastructure.in.web;

import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.port.in.GetVehiclesUseCase;
import com.urbanmove.fleet.domain.port.in.ReserveVehicleUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class FleetController {

    private final GetVehiclesUseCase getVehiclesUseCase;
    private final ReserveVehicleUseCase reserveVehicleUseCase;

    public FleetController(GetVehiclesUseCase getVehiclesUseCase, ReserveVehicleUseCase reserveVehicleUseCase) {
        this.getVehiclesUseCase = getVehiclesUseCase;
        this.reserveVehicleUseCase = reserveVehicleUseCase;
    }

    @GetMapping("/vehicles")
    public ResponseEntity<List<Vehicle>> getVehicles(@RequestParam(required = false, defaultValue = "false") boolean availableOnly) {
        List<Vehicle> vehicles = availableOnly ? getVehiclesUseCase.getAvailableVehicles() : getVehiclesUseCase.getAllVehicles();
        return ResponseEntity.ok(vehicles);
    }

    public record ReserveRequest(UUID vehicleId, String userId) {}

    @PostMapping("/reservations")
    public ResponseEntity<?> reserveVehicle(@RequestBody ReserveRequest request) {
        try {
            Reservation reservation = reserveVehicleUseCase.reserve(request.vehicleId(), request.userId());
            return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
