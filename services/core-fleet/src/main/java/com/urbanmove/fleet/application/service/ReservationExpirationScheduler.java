package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import com.urbanmove.fleet.domain.port.out.VehicleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationRepositoryPort reservationRepository;
    private final VehicleRepositoryPort vehicleRepository;
    private final EventPublisherPort eventPublisher;
    private final Clock clock;

    public ReservationExpirationScheduler(
            ReservationRepositoryPort reservationRepository,
            VehicleRepositoryPort vehicleRepository,
            EventPublisherPort eventPublisher,
            Clock clock
    ) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(fixedDelayString = "${fleet.reservation.expiration-interval:10000}")
    @Transactional
    public int sweepExpiredReservations() {
        Instant now = Instant.now(clock);
        List<Reservation> expiredReservations = reservationRepository.findExpiredActive(now);

        if (expiredReservations.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Reservation reservation : expiredReservations) {
            try {
                // Marcar reserva como EXPIRED
                Reservation expired = reservation.expire();
                reservationRepository.save(expired);

                // Liberar el vehículo de vuelta a AVAILABLE si sigue en RESERVED
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

                count++;
            } catch (Exception ex) {
                log.error("Failed to expire reservation [{}]: {}", reservation.id(), ex.getMessage(), ex);
            }
        }

        log.info("Reservation expiration sweep: expired [{}] reservations at [{}]", count, now);
        return count;
    }
}
