package com.urbanmove.telemetry;

import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryControllerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    private TelemetryController controller;

    @BeforeEach
    void setUp() {
        controller = new TelemetryController(jdbc, events);
    }

    @Test
    @DisplayName("ingest debe rechazar lecturas sin vehicleId o con batería fuera de rango")
    void shouldRejectInvalidReading() {
        assertThatThrownBy(() -> controller.ingest(new TelemetryController.Reading(null, 50, 28.1, -15.4)))
                .isInstanceOf(ApiException.class);

        assertThatThrownBy(() -> controller.ingest(new TelemetryController.Reading(UUID.randomUUID().toString(), 150, 28.1, -15.4)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("ingest debe emitir TelemetryReceived y VehicleBatteryLow al cruzar umbral <= 20%")
    void shouldEmitBatteryLowWhenCrossingThreshold() {
        String vehicleId = "11111111-1111-1111-1111-111111111111";
        when(jdbc.query(contains("SELECT battery_pct FROM telemetry_reading"), any(RowMapper.class), eq(UUID.fromString(vehicleId))))
                .thenReturn(List.of(25));

        controller.ingest(new TelemetryController.Reading(vehicleId, 18, 28.1, -15.4));

        verify(events).append(eq("telemetry"), eq(vehicleId), eq("TelemetryReceived"), anyMap());
        verify(events).append(eq("telemetry"), eq(vehicleId), eq("VehicleBatteryLow"), anyMap());
    }
}
