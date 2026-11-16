package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.event.ReservationCreatedEvent;
import com.urbanmove.fleet.domain.event.TripRecordedEvent;
import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Ride;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.port.in.GetVehiclesUseCase;
import com.urbanmove.fleet.domain.port.in.ManageRidesUseCase;
import com.urbanmove.fleet.domain.port.in.ReserveVehicleUseCase;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import com.urbanmove.fleet.domain.port.out.RideRepositoryPort;
import com.urbanmove.fleet.domain.port.out.VehicleRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FleetApplicationService implements GetVehiclesUseCase, ReserveVehicleUseCase, ManageRidesUseCase {

    private final VehicleRepositoryPort vehicleRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final RideRepositoryPort rideRepository;
    private final EventPublisherPort eventPublisher;

    public FleetApplicationService(
            VehicleRepositoryPort vehicleRepository,
            ReservationRepositoryPort reservationRepository,
            RideRepositoryPort rideRepository,
            EventPublisherPort eventPublisher
    ) {
        this.vehicleRepository = vehicleRepository;
        this.reservationRepository = reservationRepository;
        this.rideRepository = rideRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getAvailableVehicles() {
        return vehicleRepository.findAvailable();
    }

    @Override
    @Transactional
    public Reservation reserve(UUID vehicleId, String userId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + vehicleId));

        Vehicle reservedVehicle = vehicle.markAsReserved();
        vehicleRepository.save(reservedVehicle);

        Reservation reservation = Reservation.createNew(vehicle.id(), userId != null ? userId : "guest-user");
        Reservation savedReservation = reservationRepository.save(reservation);

        ReservationCreatedEvent event = ReservationCreatedEvent.of(
                savedReservation.id(),
                vehicle.id(),
                vehicle.code(),
                savedReservation.userId()
        );
        eventPublisher.publish(event);

        return savedReservation;
    }

    @Override
    @Transactional
    public Ride startRide(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        Vehicle vehicle = vehicleRepository.findById(reservation.vehicleId())
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + reservation.vehicleId()));

        // Cambiar vehículo a IN_USE
        Vehicle inUseVehicle = new Vehicle(
                vehicle.id(), vehicle.code(), vehicle.type(),
                VehicleStatus.IN_USE, vehicle.batteryPercentage(),
                vehicle.latitude(), vehicle.longitude()
        );
        vehicleRepository.save(inUseVehicle);

        Ride ride = Ride.start(reservation.id(), vehicle.id(), reservation.userId(), vehicle.latitude(), vehicle.longitude());
        return rideRepository.save(ride);
    }

    @Override
    @Transactional
    public Ride finishRide(UUID rideId, double endLatitude, double endLongitude) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found: " + rideId));

        Ride completedRide = ride.complete(endLatitude, endLongitude);
        Ride savedRide = rideRepository.save(completedRide);

        // Actualizar vehículo a AVAILABLE en la nueva posición y restando batería simulada
        Vehicle vehicle = vehicleRepository.findById(ride.vehicleId())
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + ride.vehicleId()));

        int remainingBattery = Math.max(5, vehicle.batteryPercentage() - 8);
        Vehicle updatedVehicle = new Vehicle(
                vehicle.id(), vehicle.code(), vehicle.type(),
                VehicleStatus.AVAILABLE, remainingBattery,
                endLatitude, endLongitude
        );
        vehicleRepository.save(updatedVehicle);

        // Completar reserva
        Reservation reservation = reservationRepository.findById(ride.reservationId())
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + ride.reservationId()));
        Reservation completedReservation = new Reservation(
                reservation.id(), reservation.vehicleId(), reservation.userId(),
                reservation.reservedAt(), Reservation.ReservationStatus.COMPLETED
        );
        reservationRepository.save(completedReservation);

        // Publicar evento TripRecorded a SQS (para que la Lambda lo guarde en S3)
        TripRecordedEvent tripEvent = TripRecordedEvent.of(
                savedRide.id(),
                savedRide.reservationId(),
                savedRide.vehicleId(),
                vehicle.code(),
                savedRide.userId(),
                savedRide.startedAt(),
                savedRide.finishedAt(),
                savedRide.distanceKm(),
                savedRide.costAmount(),
                endLatitude,
                endLongitude
        );
        eventPublisher.publish(tripEvent);

        return savedRide;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }
}
