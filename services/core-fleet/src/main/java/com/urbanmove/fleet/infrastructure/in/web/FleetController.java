package com.urbanmove.fleet.infrastructure.in.web;

import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Ride;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.port.in.GetVehiclesUseCase;
import com.urbanmove.fleet.domain.port.in.ManageRidesUseCase;
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
    private final ManageRidesUseCase manageRidesUseCase;

    public FleetController(
            GetVehiclesUseCase getVehiclesUseCase,
            ReserveVehicleUseCase reserveVehicleUseCase,
            ManageRidesUseCase manageRidesUseCase
    ) {
        this.getVehiclesUseCase = getVehiclesUseCase;
        this.reserveVehicleUseCase = reserveVehicleUseCase;
        this.manageRidesUseCase = manageRidesUseCase;
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

    public record StartRideRequest(UUID reservationId) {}

    @PostMapping("/rides/start")
    public ResponseEntity<?> startRide(@RequestBody StartRideRequest request) {
        try {
            Ride ride = manageRidesUseCase.startRide(request.reservationId());
            return ResponseEntity.status(HttpStatus.CREATED).body(ride);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    public record FinishRideRequest(double endLatitude, double endLongitude) {}

    @PostMapping("/rides/{rideId}/finish")
    public ResponseEntity<?> finishRide(@PathVariable UUID rideId, @RequestBody FinishRideRequest request) {
        try {
            Ride ride = manageRidesUseCase.finishRide(rideId, request.endLatitude(), request.endLongitude());
            return ResponseEntity.ok(ride);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/rides")
    public ResponseEntity<List<Ride>> getAllRides() {
        return ResponseEntity.ok(manageRidesUseCase.getAllRides());
    }
}
