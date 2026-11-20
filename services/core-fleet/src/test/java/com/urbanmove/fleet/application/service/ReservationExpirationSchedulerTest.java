package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.model.VehicleType;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import com.urbanmove.fleet.domain.port.out.VehicleRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationSchedulerTest {

    @Mock
    private ReservationRepositoryPort reservationRepository;

    @Mock
    private VehicleRepositoryPort vehicleRepository;

    @Mock
    private EventPublisherPort eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T22:30:00Z"), ZoneId.of("UTC"));

    private ReservationExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReservationExpirationScheduler(reservationRepository, vehicleRepository, eventPublisher, clock);
    }

    @Test
    @DisplayName("Debe marcar como EXPIRED las reservas caducadas y liberar los vehículos a AVAILABLE")
    void shouldSweepExpiredReservationsAndReleaseVehicles() {
        UUID vehicleId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant pastTime = Instant.parse("2026-11-20T22:00:00Z");
        Instant expirationTime = pastTime.plusSeconds(900); // 22:15:00Z (before 22:30:00Z)

        Reservation expiredReservation = new Reservation(
                reservationId, vehicleId, "user-charlie", pastTime, expirationTime, Reservation.ReservationStatus.ACTIVE
        );
        Vehicle reservedVehicle = new Vehicle(
                vehicleId, "SYT-999", VehicleType.SCOOTER, VehicleStatus.RESERVED, 75, 28.12, -15.42
        );

        when(reservationRepository.findExpiredActive(Instant.now(clock))).thenReturn(List.of(expiredReservation));
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(reservedVehicle));

        int expiredCount = scheduler.sweepExpiredReservations();

        assertThat(expiredCount).isEqualTo(1);

        // Verifica que la reserva se guarda como EXPIRED
        ArgumentCaptor<Reservation> resCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(resCaptor.capture());
        assertThat(resCaptor.getValue().status()).isEqualTo(Reservation.ReservationStatus.EXPIRED);

        // Verifica que el vehículo se libera a AVAILABLE
        ArgumentCaptor<Vehicle> vehCaptor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(vehCaptor.capture());
        assertThat(vehCaptor.getValue().status()).isEqualTo(VehicleStatus.AVAILABLE);
    }
}
