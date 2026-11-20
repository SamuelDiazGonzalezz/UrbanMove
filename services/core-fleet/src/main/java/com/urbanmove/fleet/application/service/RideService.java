package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.event.TripRecordedEvent;
import com.urbanmove.fleet.domain.exception.ConcurrencyConflictException;
import com.urbanmove.fleet.domain.exception.ResourceNotFoundException;
import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Ride;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.port.in.ManageRidesUseCase;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import com.urbanmove.fleet.domain.port.out.RideRepositoryPort;
import com.urbanmove.fleet.domain.port.out.VehicleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RideService implements ManageRidesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    private final VehicleRepositoryPort vehicleRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final RideRepositoryPort rideRepository;
    private final EventPublisherPort eventPublisher;
    private final Clock clock;

    public RideService(
            VehicleRepositoryPort vehicleRepository,
            ReservationRepositoryPort reservationRepository,
            RideRepositoryPort rideRepository,
            EventPublisherPort eventPublisher,
            Clock clock
    ) {
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository);
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.rideRepository = Objects.requireNonNull(rideRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public Ride startRide(UUID reservationId) {
        Objects.requireNonNull(reservationId, "reservationId cannot be null");
        Instant now = Instant.now(clock);

        // 1. Obtener y bloquear la reserva
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.status() != Reservation.ReservationStatus.ACTIVE) {
            throw new ConcurrencyConflictException("Cannot start ride for reservation in status: " + reservation.status());
        }
        if (reservation.isExpired(now)) {
            throw new ConcurrencyConflictException("Reservation has already expired at " + reservation.expiresAt());
        }

        // 2. Verificar idempotencia de viaje
        if (rideRepository.findActiveByReservationId(reservationId).isPresent()) {
            throw new ConcurrencyConflictException("A ride is already active for reservation: " + reservationId);
        }

        // 3. Obtener y bloquear vehículo
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(reservation.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + reservation.vehicleId()));

        if (vehicle.status() != VehicleStatus.RESERVED) {
            throw new ConcurrencyConflictException("Vehicle must be in RESERVED status, currently: " + vehicle.status());
        }

        // 4. Transicionar vehículo a IN_USE
        Vehicle inUseVehicle = new Vehicle(
                vehicle.id(), vehicle.code(), vehicle.type(),
                VehicleStatus.IN_USE, vehicle.batteryPercentage(),
                vehicle.latitude(), vehicle.longitude()
        );
        vehicleRepository.save(inUseVehicle);

        // 5. Completar reserva
        Reservation completedReservation = reservation.complete();
        reservationRepository.save(completedReservation);

        // 6. Iniciar viaje
        Ride ride = Ride.start(reservation.id(), vehicle.id(), reservation.userId(),
                vehicle.latitude(), vehicle.longitude(), now);
        try {
            return rideRepository.save(ride);
        } catch (DataIntegrityViolationException ex) {
            throw new ConcurrencyConflictException("A ride is already active for this reservation or vehicle", ex);
        }
    }

    @Override
    @Transactional
    public Ride finishRide(UUID rideId, double endLatitude, double endLongitude) {
        Objects.requireNonNull(rideId, "rideId cannot be null");
        Instant now = Instant.now(clock);

        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));

        if (ride.status() != Ride.RideStatus.IN_PROGRESS) {
            throw new ConcurrencyConflictException("Ride is already finished or not in progress: " + ride.status());
        }

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(ride.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + ride.vehicleId()));

        // Completar viaje calculando distancia y coste
        Ride completedRide = ride.complete(endLatitude, endLongitude, now);
        Ride savedRide = rideRepository.save(completedRide);

        // Actualizar estado del vehículo: si batería < 20% -> MAINTENANCE, sino AVAILABLE
        VehicleStatus newStatus = vehicle.batteryPercentage() < 20
                ? VehicleStatus.MAINTENANCE
                : VehicleStatus.AVAILABLE;
        Vehicle updatedVehicle = new Vehicle(
                vehicle.id(), vehicle.code(), vehicle.type(),
                newStatus, vehicle.batteryPercentage(),
                endLatitude, endLongitude
        );
        vehicleRepository.save(updatedVehicle);

        // Emitir evento con todos los campos del sobre
        TripRecordedEvent event = TripRecordedEvent.of(
                savedRide.id(),
                savedRide.reservationId(),
                savedRide.vehicleId(),
                vehicle.code(),
                savedRide.userId(),
                savedRide.startedAt(),
                savedRide.finishedAt(),
                savedRide.distanceKm(),
                savedRide.costAmount(),
                savedRide.endLatitude(),
                savedRide.endLongitude()
        );
        eventPublisher.publish(event);

        log.info("Finished ride [{}] for vehicle [{}], cost: {} EUR",
                savedRide.id(), vehicle.code(), savedRide.costAmount());
        return savedRide;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }
}
