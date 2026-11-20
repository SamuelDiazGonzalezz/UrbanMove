package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.event.ReservationCreatedEvent;
import com.urbanmove.fleet.domain.exception.ConcurrencyConflictException;
import com.urbanmove.fleet.domain.exception.ResourceNotFoundException;
import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.port.in.CancelReservationUseCase;
import com.urbanmove.fleet.domain.port.in.ReserveVehicleUseCase;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import com.urbanmove.fleet.domain.port.out.VehicleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReservationService implements ReserveVehicleUseCase, CancelReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final VehicleRepositoryPort vehicleRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final EventPublisherPort eventPublisher;
    private final Clock clock;

    public ReservationService(
            VehicleRepositoryPort vehicleRepository,
            ReservationRepositoryPort reservationRepository,
            EventPublisherPort eventPublisher,
            Clock clock
    ) {
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository, "vehicleRepository cannot be null");
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    @Transactional
    public Reservation reserve(UUID vehicleId, String userId) {
        Objects.requireNonNull(vehicleId, "vehicleId cannot be null");
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : "guest-user";
        Instant now = Instant.now(clock);

        // 1. Bloqueo pesimista sobre el vehículo para serializar peticiones concurrentes
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        // 2. Invariante de dominio: sólo se puede reservar un vehículo AVAILABLE y con batería suficiente
        if (vehicle.status() != VehicleStatus.AVAILABLE) {
            throw new ConcurrencyConflictException(
                    "Vehicle " + vehicle.code() + " is currently in status " + vehicle.status() + " and cannot be reserved"
            );
        }
        if (vehicle.batteryPercentage() < 15) {
            throw new ConcurrencyConflictException(
                    "Vehicle " + vehicle.code() + " has low battery (" + vehicle.batteryPercentage() + "%) and cannot be reserved"
            );
        }

        // 3. Transición de estado en vehículo
        Vehicle reservedVehicle = vehicle.markAsReserved();
        vehicleRepository.save(reservedVehicle);

        // 4. Creación y persistencia de la reserva (con expires_at a 15 min)
        Reservation reservation = Reservation.createNew(vehicle.id(), effectiveUserId, now, 15);
        Reservation savedReservation;
        try {
            savedReservation = reservationRepository.save(reservation);
        } catch (DataIntegrityViolationException ex) {
            // Safety net: el índice único parcial 'uq_active_reservation_per_vehicle' ha disparado
            throw new ConcurrencyConflictException("Vehicle already has an active reservation", ex);
        }

        // 5. Emisión de evento de dominio
        ReservationCreatedEvent event = ReservationCreatedEvent.of(
                savedReservation.id(),
                vehicle.id(),
                vehicle.code(),
                savedReservation.userId()
        );
        eventPublisher.publish(event);

        log.info("Reservation [{}] created for vehicle [{}] and user [{}] until [{}]",
                savedReservation.id(), vehicle.code(), effectiveUserId, savedReservation.expiresAt());

        return savedReservation;
    }

    @Override
    @Transactional
    public Reservation cancelReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.status() != Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation is not ACTIVE: " + reservation.status());
        }

        Reservation cancelledReservation = reservation.cancel();
        reservationRepository.save(cancelledReservation);

        // Liberar vehículo a AVAILABLE si sigue en RESERVED
        vehicleRepository.findByIdForUpdate(reservation.vehicleId()).ifPresent(v -> {
            if (v.status() == VehicleStatus.RESERVED) {
                Vehicle availableVehicle = new Vehicle(
                        v.id(), v.code(), v.type(), VehicleStatus.AVAILABLE,
                        v.batteryPercentage(), v.latitude(), v.longitude()
                );
                vehicleRepository.save(availableVehicle);
            }
        });

        return cancelledReservation;
    }
}
