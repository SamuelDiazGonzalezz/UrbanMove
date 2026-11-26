package com.urbanmove.zones;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZoneServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    private ZoneService service;

    @BeforeEach
    void setUp() {
        service = new ZoneService(jdbc, events);
    }

    @Test
    @DisplayName("create debe insertar la zona y emitir ZoneCreated")
    void shouldCreateZoneAndEmitEvent() {
        ZoneService.Zone created = new ZoneService.Zone("vegueta", "Vegueta", 28.1010, -15.4140, 600, 3, true);
        when(jdbc.queryForObject(contains("SELECT count(*) FROM zone WHERE zone_id = ?"), eq(Integer.class), eq("vegueta")))
                .thenReturn(0);
        when(jdbc.query(contains("SELECT * FROM zone WHERE active"), any(RowMapper.class)))
                .thenReturn(List.of(created));

        ZoneService.Zone result = service.create(new ZoneService.NewZone("vegueta", "Vegueta", 28.1010, -15.4140, 600, 3));

        assertThat(result.zoneId()).isEqualTo("vegueta");
        verify(jdbc).update(contains("INSERT INTO zone"), eq("vegueta"), eq("Vegueta"), eq(28.1010), eq(-15.4140), eq(600), eq(3));
        verify(events).append(eq("zone"), eq("vegueta"), eq("ZoneCreated"), anyMap());
    }

    @Test
    @DisplayName("create debe rechazar id inválido o zona duplicada")
    void shouldRejectInvalidOrDuplicateZone() {
        assertThatThrownBy(() -> service.create(new ZoneService.NewZone("INVALID_ID!", "Bad", 28.1, -15.4, 500, 2)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("minúsculas");

        when(jdbc.queryForObject(contains("SELECT count(*) FROM zone WHERE zone_id = ?"), eq(Integer.class), eq("triana")))
                .thenReturn(1);

        assertThatThrownBy(() -> service.create(new ZoneService.NewZone("triana", "Triana", 28.1, -15.4, 500, 2)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ya existe");
    }

    @Test
    @DisplayName("locate debe devolver la zona más cercana a las coordenadas dadas")
    void shouldLocateNearestZone() {
        ZoneService.Zone vegueta = new ZoneService.Zone("vegueta", "Vegueta", 28.1010, -15.4140, 600, 3, true);
        ZoneService.Zone canteras = new ZoneService.Zone("las-canteras", "Las Canteras", 28.1355, -15.4385, 700, 4, true);
        when(jdbc.query(contains("SELECT * FROM zone WHERE active"), any(RowMapper.class)))
                .thenReturn(List.of(vegueta, canteras));

        ZoneService.Zone nearest = service.locate(28.1350, -15.4380);
        assertThat(nearest.zoneId()).isEqualTo("las-canteras");
    }
}
