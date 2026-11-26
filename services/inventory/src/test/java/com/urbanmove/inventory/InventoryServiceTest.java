package com.urbanmove.inventory;

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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private TransactionTemplate tx;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        service = new InventoryService(jdbc, events, restClientBuilder, tx);
    }

    @Test
    @DisplayName("purchase debe rechazar tipos de vehículo inválidos")
    void shouldRejectInvalidVehicleType() {
        assertThatThrownBy(() -> service.purchase(new InventoryService.Purchase("X-1", "SPACESHIP", "Model", "vegueta")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tipo de vehículo no válido");

        verify(events, never()).append(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("retire debe actualizar vehicle_asset y emitir VehicleRetired")
    void shouldRetireVehicleAndEmitEvent() {
        String id = "11111111-1111-1111-1111-111111111111";
        when(jdbc.update(contains("UPDATE vehicle_asset SET retired_at = now()"), eq("Avería grave"), eq(id)))
                .thenReturn(1);

        service.retire(id, "Avería grave");

        verify(events).append(eq("vehicle"), eq(id), eq("VehicleRetired"), anyMap());
    }

    @Test
    @DisplayName("retire debe fallar con 409 si el vehículo no existe o ya está retirado")
    void shouldThrowConflictWhenRetireFails() {
        String id = "11111111-1111-1111-1111-111111111111";
        when(jdbc.update(contains("UPDATE vehicle_asset SET retired_at = now()"), anyString(), eq(id)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.retire(id, "Obsoleto"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ya está dado de baja");

        verify(events, never()).append(anyString(), anyString(), anyString(), anyMap());
    }
}
