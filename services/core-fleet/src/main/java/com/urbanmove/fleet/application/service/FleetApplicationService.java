package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Ride;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.port.in.GetVehiclesUseCase;
import com.urbanmove.fleet.domain.port.in.ManageRidesUseCase;
import com.urbanmove.fleet.domain.port.in.ReserveVehicleUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Facade decorador para preservar compatibilidad con clientes existentes,
 * delegando en los servicios especializados (SRP).
 */
@Service
@Primary
public class FleetApplicationService implements GetVehiclesUseCase, ReserveVehicleUseCase, ManageRidesUseCase {

    private final VehicleAvailabilityService availabilityService;
    private final ReservationService reservationService;
    private final RideService rideService;

    public FleetApplicationService(
            VehicleAvailabilityService availabilityService,
            ReservationService reservationService,
            RideService rideService
    ) {
        this.availabilityService = Objects.requireNonNull(availabilityService);
        this.reservationService = Objects.requireNonNull(reservationService);
        this.rideService = Objects.requireNonNull(rideService);
    }

    @Override
    public List<Vehicle> getAllVehicles() {
        return availabilityService.getAllVehicles();
    }

    @Override
    public List<Vehicle> getAvailableVehicles() {
        return availabilityService.getAvailableVehicles();
    }

    @Override
    public Reservation reserve(UUID vehicleId, String userId) {
        return reservationService.reserve(vehicleId, userId);
    }

    @Override
    public Ride startRide(UUID reservationId) {
        return rideService.startRide(reservationId);
    }

    @Override
    public Ride finishRide(UUID rideId, double endLatitude, double endLongitude) {
        return rideService.finishRide(rideId, endLatitude, endLongitude);
    }

    @Override
    public List<Ride> getAllRides() {
        return rideService.getAllRides();
    }
}
