package com.urbanmove.fleet.application.service;

import com.urbanmove.fleet.domain.event.ReservationCreatedEvent;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FleetApplicationServiceTest {

    @Mock
    private VehicleRepositoryPort vehicleRepository;

    @Mock
    private ReservationRepositoryPort reservationRepository;

    @Mock
    private EventPublisherPort eventPublisher;

    private FleetApplicationService fleetService;

    @BeforeEach
    void setUp() {
        fleetService = new FleetApplicationService(vehicleRepository, reservationRepository, eventPublisher);
    }

    @Test
    @DisplayName("Debe reservar un vehículo disponible, guardarlo en BD y publicar evento en SQS")
    void shouldReserveVehicleSuccessfully() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(vehicleId, "SCOOT-100", VehicleType.SCOOTER, VehicleStatus.AVAILABLE, 85, 39.46, -0.37);

        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = fleetService.reserve(vehicleId, "user-alice");

        assertThat(result).isNotNull();
        assertThat(result.vehicleId()).isEqualTo(vehicleId);
        assertThat(result.userId()).isEqualTo("user-alice");
        assertThat(result.status()).isEqualTo(Reservation.ReservationStatus.ACTIVE);

        // Verificar que el vehículo se guardó como RESERVED
        ArgumentCaptor<Vehicle> vehicleCaptor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(vehicleCaptor.capture());
        assertThat(vehicleCaptor.getValue().status()).isEqualTo(VehicleStatus.RESERVED);

        // Verificar publicación del evento cloud
        ArgumentCaptor<ReservationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ReservationCreatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().vehicleCode()).isEqualTo("SCOOT-100");
    }

    @Test
    @DisplayName("Falla al reservar si el vehículo no existe")
    void shouldThrowExceptionWhenVehicleNotFound() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fleetService.reserve(vehicleId, "user-alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vehicle not found");

        verifyNoInteractions(eventPublisher);
    }
}
