package com.urbanmove.maintenance;

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
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenanceServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private MaintenanceService service;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        service = new MaintenanceService(jdbc, events, restClientBuilder, 10);
    }

    @Test
    @DisplayName("open debe rechazar tipos distintos a CHARGE o REPAIR")
    void shouldRejectInvalidOrderType() {
        assertThatThrownBy(() -> service.open("v-1", "PAINT", "Raspón", "MANUAL", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("CHARGE o REPAIR");
    }

    @Test
    @DisplayName("open debe crear orden y emitir MaintenanceOrderOpened")
    void shouldOpenOrderAndEmitEvent() {
        String vehicleId = "11111111-1111-1111-1111-111111111111";
        Map<String, Object> created = Map.of("order_id", "ord-1", "vehicle_id", vehicleId, "status", "OPEN");

        when(jdbc.queryForList(contains("WHERE status IN ('OPEN','WITHDRAWN','IN_PROGRESS')"), eq(vehicleId)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("WHERE order_id = ?::uuid"), anyString()))
                .thenReturn(List.of(created));

        Map<String, Object> result = service.open(vehicleId, "CHARGE", "Batería baja", "AUTO", null);

        assertThat(result.get("result")).isEqualTo("CREATED");
        verify(events).append(eq("maintenance-order"), anyString(), eq("MaintenanceOrderOpened"), anyMap());
    }
}
