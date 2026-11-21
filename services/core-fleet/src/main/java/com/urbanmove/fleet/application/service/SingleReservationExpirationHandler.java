package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.event.ReservationExpiredEvent;
import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import com.urbanmove.fleet.domain.port.out.VehicleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
public class SingleReservationExpirationHandler {

    private static final Logger log = LoggerFactory.getLogger(SingleReservationExpirationHandler.class);

    private final ReservationRepositoryPort reservationRepository;
    private final VehicleRepositoryPort vehicleRepository;
    private final EventPublisherPort eventPublisher;

    public SingleReservationExpirationHandler(
            ReservationRepositoryPort reservationRepository,
            VehicleRepositoryPort vehicleRepository,
            EventPublisherPort eventPublisher
    ) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    /**
     * Expira una reserva individual en una transacción aislada (REQUIRES_NEW).
     * Si falla una reserva, no contamina el contexto transaccional del lote global.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireSingleReservation(Reservation reservation, Instant now) {
        // 1. Marcar reserva como EXPIRED
        Reservation expired = reservation.expire();
        reservationRepository.save(expired);

        // 2. Liberar el vehículo de vuelta a AVAILABLE si sigue en RESERVED
        vehicleRepository.findByIdForUpdate(reservation.vehicleId()).ifPresent(v -> {
            if (v.status() == VehicleStatus.RESERVED) {
                Vehicle availableVehicle = new Vehicle(
                        v.id(), v.code(), v.type(), VehicleStatus.AVAILABLE,
                        v.batteryPercentage(), v.latitude(), v.longitude()
                );
                vehicleRepository.save(availableVehicle);
                log.info("Vehicle [{}] released to AVAILABLE after reservation [{}] expired", v.code(), reservation.id());
            }
        });

        // 3. Emitir evento de dominio ReservationExpired
        ReservationExpiredEvent event = ReservationExpiredEvent.of(
                reservation.id(),
                reservation.vehicleId(),
                reservation.userId(),
                now
        );
        eventPublisher.publish(event);
    }
}
