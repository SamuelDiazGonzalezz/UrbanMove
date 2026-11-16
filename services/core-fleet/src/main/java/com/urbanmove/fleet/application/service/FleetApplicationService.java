package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.event.ReservationCreatedEvent;
import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.port.in.GetVehiclesUseCase;
import com.urbanmove.fleet.domain.port.in.ReserveVehicleUseCase;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import com.urbanmove.fleet.domain.port.out.VehicleRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FleetApplicationService implements GetVehiclesUseCase, ReserveVehicleUseCase {

    private final VehicleRepositoryPort vehicleRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final EventPublisherPort eventPublisher;

    public FleetApplicationService(
            VehicleRepositoryPort vehicleRepository,
            ReservationRepositoryPort reservationRepository,
            EventPublisherPort eventPublisher
    ) {
        this.vehicleRepository = vehicleRepository;
        this.reservationRepository = reservationRepository;
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

        // Domain logic: validation & state transition
        Vehicle reservedVehicle = vehicle.markAsReserved();
        vehicleRepository.save(reservedVehicle);

        // Create reservation
        Reservation reservation = Reservation.createNew(vehicle.id(), userId != null ? userId : "guest-user");
        Reservation savedReservation = reservationRepository.save(reservation);

        // Publish cloud event to LocalStack SQS
        ReservationCreatedEvent event = ReservationCreatedEvent.of(
                savedReservation.id(),
                vehicle.id(),
                vehicle.code(),
                savedReservation.userId()
        );
        eventPublisher.publish(event);

        return savedReservation;
    }
}
