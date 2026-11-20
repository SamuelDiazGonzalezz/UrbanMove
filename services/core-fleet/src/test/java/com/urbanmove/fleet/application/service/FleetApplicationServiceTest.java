package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.event.ReservationCreatedEvent;
import com.urbanmove.fleet.domain.event.TripRecordedEvent;
import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Ride;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.model.VehicleType;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.domain.port.out.ReservationRepositoryPort;
import com.urbanmove.fleet.domain.port.out.RideRepositoryPort;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FleetApplicationServiceTest {

    @Mock
    private VehicleRepositoryPort vehicleRepository;

    @Mock
    private ReservationRepositoryPort reservationRepository;

    @Mock
    private RideRepositoryPort rideRepository;

    @Mock
    private EventPublisherPort eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T22:00:00Z"), ZoneId.of("UTC"));

    private FleetApplicationService fleetService;

    @BeforeEach
    void setUp() {
        var availabilityService = new VehicleAvailabilityService(vehicleRepository);
        var reservationService = new ReservationService(vehicleRepository, reservationRepository, eventPublisher, clock);
        var rideService = new RideService(vehicleRepository, reservationRepository, rideRepository, eventPublisher, clock);

        fleetService = new FleetApplicationService(availabilityService, reservationService, rideService);
    }

    @Test
    @DisplayName("Debe reservar un vehículo disponible, guardarlo en BD y publicar evento en SQS")
    void shouldReserveVehicleSuccessfully() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(vehicleId, "SCOOT-100", VehicleType.SCOOTER, VehicleStatus.AVAILABLE, 85, 39.46, -0.37);

        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = fleetService.reserve(vehicleId, "user-alice");

        assertThat(result).isNotNull();
        assertThat(result.vehicleId()).isEqualTo(vehicleId);
        assertThat(result.userId()).isEqualTo("user-alice");
        assertThat(result.status()).isEqualTo(Reservation.ReservationStatus.ACTIVE);
        assertThat(result.expiresAt()).isAfter(result.reservedAt());

        ArgumentCaptor<Vehicle> vehicleCaptor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(vehicleCaptor.capture());
        assertThat(vehicleCaptor.getValue().status()).isEqualTo(VehicleStatus.RESERVED);

        ArgumentCaptor<ReservationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ReservationCreatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().vehicleCode()).isEqualTo("SCOOT-100");
    }

    @Test
    @DisplayName("Debe iniciar y finalizar viaje, actualizando estados y emitiendo TripRecordedEvent a SQS")
    void shouldStartAndFinishRideSuccessfully() {
        UUID vehicleId = UUID.randomUUID();
        UUID resId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        Vehicle reservedVehicle = new Vehicle(vehicleId, "SYT-001", VehicleType.ELECTRIC_BICYCLE, VehicleStatus.RESERVED, 90, 28.12, -15.42);
        Reservation reservation = Reservation.createNew(vehicleId, "user-bob", Instant.now(clock), 15);
        Ride inProgressRide = Ride.start(resId, vehicleId, "user-bob", 28.12, -15.42, Instant.now(clock));

        when(reservationRepository.findByIdForUpdate(resId)).thenReturn(Optional.of(reservation));
        when(rideRepository.findActiveByReservationId(resId)).thenReturn(Optional.empty());
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(reservedVehicle));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        // Iniciar viaje
        Ride started = fleetService.startRide(resId);
        assertThat(started).isNotNull();

        // Finalizar viaje
        when(rideRepository.findByIdForUpdate(rideId)).thenReturn(Optional.of(inProgressRide));
        Ride finished = fleetService.finishRide(rideId, 28.13, -15.43);

        assertThat(finished.status()).isEqualTo(Ride.RideStatus.COMPLETED);
        assertThat(finished.distanceKm()).isGreaterThan(0);
        assertThat(finished.costAmount()).isGreaterThan(0);

        // Verificar publicación del evento TripRecordedEvent para la Lambda y S3
        ArgumentCaptor<TripRecordedEvent> tripCaptor = ArgumentCaptor.forClass(TripRecordedEvent.class);
        verify(eventPublisher).publish(tripCaptor.capture());
        assertThat(tripCaptor.getValue().vehicleCode()).isEqualTo("SYT-001");
    }
}
