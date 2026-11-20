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
    public ResponseEntity<Reservation> reserveVehicle(@RequestBody ReserveRequest request) {
        Reservation reservation = reserveVehicleUseCase.reserve(request.vehicleId(), request.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
    }

    public record StartRideRequest(UUID reservationId) {}

    @PostMapping("/rides/start")
    public ResponseEntity<Ride> startRide(@RequestBody StartRideRequest request) {
        Ride ride = manageRidesUseCase.startRide(request.reservationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ride);
    }

    public record FinishRideRequest(double endLatitude, double endLongitude) {}

    @PostMapping("/rides/{rideId}/finish")
    public ResponseEntity<Ride> finishRide(@PathVariable UUID rideId, @RequestBody FinishRideRequest request) {
        Ride ride = manageRidesUseCase.finishRide(rideId, request.endLatitude(), request.endLongitude());
        return ResponseEntity.ok(ride);
    }

    @GetMapping("/rides")
    public ResponseEntity<List<Ride>> getAllRides() {
        return ResponseEntity.ok(manageRidesUseCase.getAllRides());
    }
}
