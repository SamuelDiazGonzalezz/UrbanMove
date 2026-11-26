package com.urbanmove.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.common.events.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private ObjectMapper objectMapper;
    private HistoryService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new HistoryService(jdbc, objectMapper);
    }

    @Test
    @DisplayName("eventTypes debe suscribirse a todos los eventos (*)")
    void shouldSubscribeToAllEvents() {
        assertThat(service.eventTypes()).containsExactly("*");
    }

    @Test
    @DisplayName("handle debe insertar la entrada en history_entry")
    void shouldInsertHistoryEntry() throws Exception {
        String eventId = UUID.randomUUID().toString();
        var payload = objectMapper.readTree("{\"userId\":\"u-1\",\"vehicleId\":\"v-1\",\"zoneId\":\"vegueta\"}");
        EventEnvelope envelope = new EventEnvelope(eventId, "VehiclePickedUp", 1,
                "urbanmove.rides", Instant.now(), Instant.now(), "corr-1", null, "vehicle-ride", "v-1", payload);

        service.handle(envelope);

        verify(jdbc).update(contains("INSERT INTO history_entry"), eq(UUID.fromString(eventId)),
                eq("VehiclePickedUp"), any(), any(), any(),
                eq("u-1"), eq("v-1"), eq("vegueta"), anyString());
    }
}
