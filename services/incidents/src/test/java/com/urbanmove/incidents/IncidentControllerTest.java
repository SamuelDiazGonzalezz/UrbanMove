package com.urbanmove.incidents;

import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    private IncidentController controller;

    @BeforeEach
    void setUp() {
        controller = new IncidentController(jdbc, events);
    }

    @Test
    @DisplayName("report debe rechazar tipos de incidencia inválidos")
    void shouldRejectInvalidIncidentType() {
        assertThatThrownBy(() -> controller.report(new IncidentController.Report("v-1", "INVALID_TYPE", "Fallo")))
                .isInstanceOf(ApiException.class);

        verify(events, never()).append(anyString(), anyString(), anyString(), anyMap());
    }
}
