package com.urbanmove.reservations;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
class ReservationServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    @Mock
    private TransactionTemplate tx;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        // Ejecutar inmediatamente los callbacks transaccionales en pruebas unitarias
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(tx).execute(any());

        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());

        service = new ReservationService(jdbc, events, tx, 300);
    }

    private ReservationService.VehicleView mockVehicle(String id, String state) {
        return new ReservationService.VehicleView(id, "SCOOT-99", "SCOOTER", state, "zone-1", 80);
    }

    private ReservationService.Reservation mockReservation(String resId, String vehicleId, String userId, String status) {
        return new ReservationService.Reservation(
                resId, vehicleId, "SCOOT-99", userId, "zone-1", status,
                Instant.now(), Instant.now().plusSeconds(300), null, null
        );
    }

    @Test
    @DisplayName("reserve debe crear la reserva y publicar VehicleReserved cuando el vehículo está AVAILABLE")
    void shouldCreateReservationWhenAvailable() {
        String vehicleId = UUID.randomUUID().toString();
        ReservationService.VehicleView vehicle = mockVehicle(vehicleId, "AVAILABLE");

        // mock para vehicle(vehicleId)
        when(jdbc.query(contains("SELECT * FROM vehicle_view WHERE vehicle_id = ?"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(vehicle));
        // mock para get(reservationId)
        when(jdbc.query(contains("SELECT * FROM reservation WHERE reservation_id = ?"), any(RowMapper.class), anyString()))
                .thenReturn(List.of(mockReservation("res-1", vehicleId, "user-ana", "ACTIVE")));

        ReservationService.Reservation res = service.reserve(vehicleId, "user-ana");

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("ACTIVE");

        verify(jdbc).update(contains("INSERT INTO reservation (reservation_id, vehicle_id"), any(), eq(vehicleId),
                eq("SCOOT-99"), eq("user-ana"), eq("zone-1"), any());
        verify(events).append(eq("reservation"), anyString(), eq("VehicleReserved"), anyMap());
    }

    @Test
    @DisplayName("reserve debe lanzar ApiException y registrar rechazo cuando el vehículo no está AVAILABLE")
    void shouldRejectReservationWhenVehicleNotAvailable() {
        String vehicleId = UUID.randomUUID().toString();
        ReservationService.VehicleView vehicle = mockVehicle(vehicleId, "IN_USE");

        when(jdbc.query(contains("SELECT * FROM vehicle_view WHERE vehicle_id = ?"), any(RowMapper.class), eq(vehicleId)))
                .thenReturn(List.of(vehicle));

        assertThatThrownBy(() -> service.reserve(vehicleId, "user-ana"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("El vehículo ya no está disponible");

        verify(events).append(eq("reservation-attempt"), eq("user-ana"), eq("ReservationRejected"), anyMap());
        verify(jdbc, never()).update(contains("INSERT INTO reservation (reservation_id, vehicle_id"), any(Object[].class));
    }

    @Test
    @DisplayName("cancel debe cancelar la reserva activa y publicar ReservationCancelled")
    void shouldCancelActiveReservation() {
        String resId = UUID.randomUUID().toString();
        ReservationService.Reservation reservation = mockReservation(resId, "veh-1", "user-ana", "ACTIVE");
        ReservationService.Reservation cancelled = new ReservationService.Reservation(
                resId, "veh-1", "SCOOT-99", "user-ana", "zone-1", "CANCELLED",
                reservation.createdAt(), reservation.expiresAt(), Instant.now(), "USER"
        );

        when(jdbc.query(contains("SELECT * FROM reservation WHERE reservation_id = ?"), any(RowMapper.class), eq(resId)))
                .thenReturn(List.of(reservation), List.of(cancelled));
        when(jdbc.update(contains("UPDATE reservation SET status = ?, ended_at = now(), end_reason = ?"), eq("CANCELLED"), eq("USER"), eq(resId)))
                .thenReturn(1);

        ReservationService.Reservation result = service.cancel(resId, "user-ana", false);

        assertThat(result.status()).isEqualTo("CANCELLED");
        verify(events).append(eq("reservation"), eq(resId), eq("ReservationCancelled"), anyMap());
    }

    @Test
    @DisplayName("cancel por un usuario que no es el dueño debe rechazar con forbidden")
    void shouldThrowForbiddenWhenUserIsNotOwner() {
        String resId = UUID.randomUUID().toString();
        ReservationService.Reservation reservation = mockReservation(resId, "veh-1", "user-ana", "ACTIVE");

        when(jdbc.query(contains("SELECT * FROM reservation WHERE reservation_id = ?"), any(RowMapper.class), eq(resId)))
                .thenReturn(List.of(reservation));

        assertThatThrownBy(() -> service.cancel(resId, "user-impostor", false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("La reserva no es tuya");
    }

    @Test
    @DisplayName("expire debe marcar la reserva vencida como EXPIRED y publicar ReservationExpired")
    void shouldExpireDueReservation() {
        String resId = UUID.randomUUID().toString();
        // Reserva expirada en el pasado
        ReservationService.Reservation reservation = new ReservationService.Reservation(
                resId, "veh-1", "SCOOT-99", "user-ana", "zone-1", "ACTIVE",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(100), null, null
        );

        when(jdbc.query(contains("SELECT * FROM reservation WHERE reservation_id = ?"), any(RowMapper.class), eq(resId)))
                .thenReturn(List.of(reservation));
        when(jdbc.update(contains("UPDATE reservation SET status = ?, ended_at = now(), end_reason = ?"), eq("EXPIRED"), eq("TIMEOUT"), eq(resId)))
                .thenReturn(1);

        String result = service.expire(resId);

        assertThat(result).isEqualTo("EXPIRED");
        verify(events).append(eq("reservation"), eq(resId), eq("ReservationExpired"), anyMap());
    }
}
