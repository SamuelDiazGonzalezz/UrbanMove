package com.urbanmove.rides;

import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RideServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    private RideService service;

    @BeforeEach
    void setUp() {
        service = new RideService(jdbc, events);
    }

    private RideService.VehicleView mockVehicle(String id, String state, String userId, String reservationId) {
        return new RideService.VehicleView(id, "MOTO-10", "MOTORCYCLE", state, "zone-1", 28.12, -15.42, 85, reservationId, userId);
    }

    private RideService.Trip mockTrip(String tripId, String vehicleId, String userId, String status) {
        return new RideService.Trip(
                tripId, vehicleId, "MOTO-10", "MOTORCYCLE", userId, null, status, "zone-1",
                28.12, -15.42, Instant.now().minusSeconds(300), 85, null, null, null, null, null, null, null, BigDecimal.ZERO
        );
    }

    @Test
    @DisplayName("start debe crear el viaje y publicar VehiclePickedUp para un vehículo AVAILABLE (walk-in)")
    void shouldStartWalkInRideWhenVehicleAvailable() {
        String vehicleId = UUID.randomUUID().toString();
        RideService.VehicleView vehicle = mockVehicle(vehicleId, "AVAILABLE", null, null);

        when(jdbc.query(contains("SELECT * FROM vehicle_view WHERE vehicle_id = ?"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(vehicle));
        when(jdbc.query(contains("SELECT * FROM trip WHERE trip_id = ?"), any(RowMapper.class), anyString()))
                .thenReturn(List.of(mockTrip("trip-1", vehicleId, "user-ana", "OPEN")));

        RideService.Trip trip = service.start(vehicleId, "user-ana");

        assertThat(trip).isNotNull();
        assertThat(trip.status()).isEqualTo("OPEN");

        verify(jdbc).update(contains("INSERT INTO trip (trip_id, vehicle_id"), any(), eq(vehicleId), eq("MOTO-10"),
                eq("MOTORCYCLE"), eq("user-ana"), isNull(), eq("zone-1"), eq(28.12), eq(-15.42), any(), eq(85));
        verify(events).append(eq("vehicle-ride"), eq(vehicleId), eq("VehiclePickedUp"), anyMap());
    }

    @Test
    @DisplayName("start debe permitir iniciar viaje si el vehículo está RESERVED por el mismo usuario")
    void shouldStartRideWhenReservedBySameUser() {
        String vehicleId = UUID.randomUUID().toString();
        RideService.VehicleView vehicle = mockVehicle(vehicleId, "RESERVED", "user-ana", "res-123");

        when(jdbc.query(contains("SELECT * FROM vehicle_view WHERE vehicle_id = ?"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(vehicle));
        when(jdbc.query(contains("SELECT * FROM trip WHERE trip_id = ?"), any(RowMapper.class), anyString()))
                .thenReturn(List.of(mockTrip("trip-1", vehicleId, "user-ana", "OPEN")));

        RideService.Trip trip = service.start(vehicleId, "user-ana");

        assertThat(trip).isNotNull();
        verify(jdbc).update(contains("INSERT INTO trip (trip_id, vehicle_id"), any(), eq(vehicleId), eq("MOTO-10"),
                eq("MOTORCYCLE"), eq("user-ana"), eq("res-123"), eq("zone-1"), eq(28.12), eq(-15.42), any(), eq(85));
        verify(events).append(eq("vehicle-ride"), eq(vehicleId), eq("VehiclePickedUp"), anyMap());
    }

    @Test
    @DisplayName("start debe fallar si el vehículo está RESERVED por otro usuario")
    void shouldRejectStartWhenReservedByAnotherUser() {
        String vehicleId = UUID.randomUUID().toString();
        RideService.VehicleView vehicle = mockVehicle(vehicleId, "RESERVED", "user-otro", "res-456");

        when(jdbc.query(contains("SELECT * FROM vehicle_view WHERE vehicle_id = ?"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(vehicle));

        assertThatThrownBy(() -> service.start(vehicleId, "user-ana"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Este vehículo no está disponible para iniciar un viaje");

        verify(events, never()).append(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("finish debe completar el viaje y publicar VehicleDroppedOff y TripRecorded")
    void shouldFinishTripSuccessfully() {
        String tripId = UUID.randomUUID().toString();
        String vehicleId = UUID.randomUUID().toString();
        RideService.Trip openTrip = mockTrip(tripId, vehicleId, "user-ana", "OPEN");
        RideService.Trip completedTrip = new RideService.Trip(
                tripId, vehicleId, "MOTO-10", "MOTORCYCLE", "user-ana", null, "COMPLETED", "zone-1",
                28.12, -15.42, openTrip.startedAt(), 85, "zone-dest", 28.14, -15.44, Instant.now(), 78, 300, 2500, BigDecimal.valueOf(3.50)
        );
        RideService.ZoneRef zone = new RideService.ZoneRef("zone-dest", "Zona Destino", 28.14, -15.44);

        when(jdbc.query(contains("SELECT * FROM trip WHERE trip_id = ?"), any(RowMapper.class), eq(tripId)))
                .thenReturn(List.of(openTrip), List.of(completedTrip));
        when(jdbc.query(contains("SELECT * FROM zone_ref WHERE zone_id = ?"), any(RowMapper.class), eq("zone-dest")))
                .thenReturn(List.of(zone));
        when(jdbc.update(contains("UPDATE trip SET status = 'COMPLETED'"), any(Object[].class))).thenReturn(1);

        RideService.Trip finished = service.finish(tripId, "user-ana", false, "zone-dest");

        assertThat(finished.status()).isEqualTo("COMPLETED");

        // Verifica que se emiten tanto el hecho físico (RAW: VehicleDroppedOff) como el enriquecido (TRUSTED: TripRecorded)
        verify(events).append(eq("vehicle-ride"), eq(vehicleId), eq("VehicleDroppedOff"), anyMap());
        verify(events).append(eq("trip"), eq(tripId), eq("TripRecorded"), anyMap());
    }
}
