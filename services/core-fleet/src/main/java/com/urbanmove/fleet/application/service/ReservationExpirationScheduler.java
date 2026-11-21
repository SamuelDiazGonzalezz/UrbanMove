package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationRepositoryPort reservationRepository;
    private final SingleReservationExpirationHandler singleReservationExpirationHandler;
    private final Clock clock;

    public ReservationExpirationScheduler(
            ReservationRepositoryPort reservationRepository,
            SingleReservationExpirationHandler singleReservationExpirationHandler,
            Clock clock
    ) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.singleReservationExpirationHandler = Objects.requireNonNull(singleReservationExpirationHandler);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * El scheduler recorre reservas expiradas sin abrir una transacción global monolithic.
     * Cada reserva se procesa de forma atómica y aislada mediante SingleReservationExpirationHandler.
     */
    @Scheduled(fixedDelayString = "${fleet.reservation.expiration-interval:10000}")
    public int sweepExpiredReservations() {
        Instant now = Instant.now(clock);
        List<Reservation> expiredReservations = reservationRepository.findExpiredActive(now);

        if (expiredReservations.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Reservation reservation : expiredReservations) {
            try {
                singleReservationExpirationHandler.expireSingleReservation(reservation, now);
                count++;
            } catch (Exception ex) {
                log.error("Failed to expire reservation [{}]: {}", reservation.id(), ex.getMessage(), ex);
            }
        }

        log.info("Reservation expiration sweep: expired [{}] reservations at [{}]", count, now);
        return count;
    }
}
