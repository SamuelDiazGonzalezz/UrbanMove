package com.urbanmove.availability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.urbanmove.common.events.EventEnvelope;
import com.urbanmove.common.events.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    private final ObjectMapper mapper = new ObjectMapper();
    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new AvailabilityService(jdbc, events);
    }

    private EventEnvelope createEnvelope(String eventType, Map<String, Object> payloadMap) {
        ObjectNode node = mapper.valueToTree(payloadMap);
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                1,
                "https://urbanmove.local/schemas/" + eventType + ".v1.json",
                Instant.now(),
                Instant.now(),
                "test-source",
                "test-producer",
                "corr-123",
                null,
                node
        );
    }

    private AvailabilityService.Vehicle mockVehicle(String vehicleId, String code, VehicleState state, int batteryPct) {
        return new AvailabilityService.Vehicle(
                vehicleId,
                code,
                "SCOOTER",
                state.name(),
                "zone-1",
                28.12,
                -15.42,
                batteryPct,
                null,
                null,
                1,
                Instant.now()
        );
    }

    @Test
    @DisplayName("VehiclePurchased debe insertar el vehículo como AVAILABLE cuando no existe")
    void shouldHandleVehiclePurchased() {
        String vehicleId = UUID.randomUUID().toString();
        EventEnvelope event = createEnvelope("VehiclePurchased", Map.of(
                "vehicleId", vehicleId,
                "code", "MOTO-01",
                "vehicleType", "MOTORCYCLE",
                "zoneId", "zone-center",
                "latitude", 28.10,
                "longitude", -15.40,
                "batteryPct", 90
        ));

        AvailabilityService.Vehicle insertedVehicle = mockVehicle(vehicleId, "MOTO-01", VehicleState.AVAILABLE, 90);
        // Primera consulta (find): vacío -> permite INSERT. Segunda consulta (get): devuelve el insertado.
        when(jdbc.query(eq("SELECT * FROM vehicle_state WHERE vehicle_id = ?::uuid"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(Collections.emptyList(), List.of(insertedVehicle));

        service.handle(event);

        verify(jdbc).update(contains("INSERT INTO vehicle_state"), eq(vehicleId), eq("MOTO-01"), eq("MOTORCYCLE"),
                eq("zone-center"), eq(28.10), eq(-15.40), eq(90));
        verify(events).append(eq("vehicle-availability"), eq(vehicleId), eq("VehicleAvailabilityChanged"), anyMap());
    }

    @Test
    @DisplayName("VehicleReserved sobre vehículo AVAILABLE debe transicionar a RESERVED")
    void shouldTransitionToReservedWhenAvailable() {
        String vehicleId = UUID.randomUUID().toString();
        String reservationId = UUID.randomUUID().toString();
        AvailabilityService.Vehicle availableVehicle = mockVehicle(vehicleId, "SCOOT-01", VehicleState.AVAILABLE, 85);
        AvailabilityService.Vehicle reservedVehicle = new AvailabilityService.Vehicle(
                vehicleId, "SCOOT-01", "SCOOTER", VehicleState.RESERVED.name(), "zone-1",
                28.12, -15.42, 85, reservationId, "user-ana", 2, Instant.now()
        );

        // onReserved llama a find(), luego transition() llama a find(), y applyChange() llama a get()
        when(jdbc.query(eq("SELECT * FROM vehicle_state WHERE vehicle_id = ?::uuid"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(availableVehicle), List.of(availableVehicle), List.of(reservedVehicle));
        when(jdbc.update(contains("UPDATE vehicle_state SET state = ?"), any(Object[].class))).thenReturn(1);

        EventEnvelope event = createEnvelope("VehicleReserved", Map.of(
                "vehicleId", vehicleId,
                "reservationId", reservationId,
                "userId", "user-ana"
        ));

        service.handle(event);

        verify(jdbc).update(contains("UPDATE vehicle_state SET state = ?"), any(Object[].class));
        verify(events).append(eq("vehicle-availability"), eq(vehicleId), eq("VehicleAvailabilityChanged"), anyMap());
    }

    @Test
    @DisplayName("VehicleReserved sobre vehículo NO disponible debe emitir VehicleReservationRejected")
    void shouldRejectReservationWhenNotAvailable() {
        String vehicleId = UUID.randomUUID().toString();
        String reservationId = UUID.randomUUID().toString();
        AvailabilityService.Vehicle current = mockVehicle(vehicleId, "SCOOT-01", VehicleState.IN_USE, 85);

        when(jdbc.query(eq("SELECT * FROM vehicle_state WHERE vehicle_id = ?::uuid"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(current));

        EventEnvelope event = createEnvelope("VehicleReserved", Map.of(
                "vehicleId", vehicleId,
                "reservationId", reservationId,
                "userId", "user-ana"
        ));

        service.handle(event);

        verify(events).append(eq("vehicle-availability"), eq(vehicleId), eq("VehicleReservationRejected"), anyMap());
        verify(jdbc, never()).update(contains("UPDATE vehicle_state SET state = ?"), any(Object[].class));
    }

    @Test
    @DisplayName("VehicleDroppedOff con batería < 20% debe transicionar de IN_USE a LOW_BATTERY")
    void shouldTransitionToLowBatteryWhenBatteryBelow20() {
        String vehicleId = UUID.randomUUID().toString();
        AvailabilityService.Vehicle inUseVehicle = mockVehicle(vehicleId, "SCOOT-01", VehicleState.IN_USE, 15);
        AvailabilityService.Vehicle lowBatteryVehicle = mockVehicle(vehicleId, "SCOOT-01", VehicleState.LOW_BATTERY, 14);

        when(jdbc.query(eq("SELECT * FROM vehicle_state WHERE vehicle_id = ?::uuid"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(inUseVehicle), List.of(lowBatteryVehicle));
        when(jdbc.query(contains("SELECT * FROM zone_ref"), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(jdbc.update(contains("UPDATE vehicle_state SET state = ?"), any(Object[].class))).thenReturn(1);

        EventEnvelope event = createEnvelope("VehicleDroppedOff", Map.of(
                "vehicleId", vehicleId,
                "batteryPct", 14,
                "latitude", 28.12,
                "longitude", -15.42
        ));

        service.handle(event);

        verify(jdbc).update(contains("UPDATE vehicle_state SET state = ?"), any(Object[].class));
        verify(events).append(eq("vehicle-availability"), eq(vehicleId), eq("VehicleAvailabilityChanged"), anyMap());
    }

    @Test
    @DisplayName("VehicleDroppedOff con batería >= 20% debe transicionar de IN_USE a AVAILABLE")
    void shouldTransitionToAvailableWhenBatteryNormal() {
        String vehicleId = UUID.randomUUID().toString();
        AvailabilityService.Vehicle inUseVehicle = mockVehicle(vehicleId, "SCOOT-01", VehicleState.IN_USE, 50);
        AvailabilityService.Vehicle availableVehicle = mockVehicle(vehicleId, "SCOOT-01", VehicleState.AVAILABLE, 50);

        when(jdbc.query(eq("SELECT * FROM vehicle_state WHERE vehicle_id = ?::uuid"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(inUseVehicle), List.of(availableVehicle));
        when(jdbc.query(contains("SELECT * FROM zone_ref"), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(jdbc.update(contains("UPDATE vehicle_state SET state = ?"), any(Object[].class))).thenReturn(1);

        EventEnvelope event = createEnvelope("VehicleDroppedOff", Map.of(
                "vehicleId", vehicleId,
                "batteryPct", 50,
                "latitude", 28.12,
                "longitude", -15.42
        ));

        service.handle(event);

        verify(jdbc).update(contains("UPDATE vehicle_state SET state = ?"), any(Object[].class));
        verify(events).append(eq("vehicle-availability"), eq(vehicleId), eq("VehicleAvailabilityChanged"), anyMap());
    }

    @Test
    @DisplayName("Transición no válida debe emitir VehicleTransitionRejected")
    void shouldEmitTransitionRejectedWhenInvalidTransition() {
        String vehicleId = UUID.randomUUID().toString();
        AvailabilityService.Vehicle current = mockVehicle(vehicleId, "SCOOT-01", VehicleState.AVAILABLE, 80);

        when(jdbc.query(eq("SELECT * FROM vehicle_state WHERE vehicle_id = ?::uuid"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(current));

        EventEnvelope event = createEnvelope("VehicleDroppedOff", Map.of(
                "vehicleId", vehicleId,
                "batteryPct", 80,
                "latitude", 28.12,
                "longitude", -15.42
        ));

        service.handle(event);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(events).append(eq("vehicle-availability"), eq(vehicleId), eq("VehicleTransitionRejected"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("currentState", "AVAILABLE")
                .containsEntry("requestedState", "AVAILABLE")
                .containsEntry("triggerEventType", "VehicleDroppedOff");
    }
}
