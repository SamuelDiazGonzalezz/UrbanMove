package com.urbanmove.relocation;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelocationServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    private RelocationService service;

    @BeforeEach
    void setUp() {
        service = new RelocationService(jdbc, events);
    }

    @Test
    @DisplayName("create debe rechazar si origen y destino son iguales")
    void shouldRejectSameFromAndToZone() {
        assertThatThrownBy(() -> service.create("vegueta", "vegueta", 2, "MANUAL", "Test", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("dos zonas distintas");
    }

    @Test
    @DisplayName("create debe insertar la orden y emitir RelocationOrderCreated")
    void shouldCreateRelocationOrder() {
        Map<String, Object> created = Map.of("order_id", "rel-1", "status", "CREATED");
        when(jdbc.queryForObject(contains("SELECT count(*) FROM relocation_order"), eq(Integer.class), eq("las-canteras")))
                .thenReturn(0);
        when(jdbc.queryForList(contains("WHERE order_id = ?::uuid"), anyString()))
                .thenReturn(List.of(created));

        Map<String, Object> result = service.create("vegueta", "las-canteras", 3, "MANUAL", "Alta demanda", null);

        assertThat(result.get("status")).isEqualTo("CREATED");
        verify(events).append(eq("relocation-order"), anyString(), eq("RelocationOrderCreated"), anyMap());
    }
}
