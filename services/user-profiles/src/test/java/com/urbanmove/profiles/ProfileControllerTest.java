package com.urbanmove.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.common.events.EventEnvelope;
import com.urbanmove.common.events.EventStore;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    private ProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ProfileController(jdbc, events);
    }

    @Test
    @DisplayName("eventTypes debe suscribirse a UserRegistered")
    void shouldSubscribeToUserRegistered() {
        assertThat(controller.eventTypes()).containsExactly("UserRegistered");
    }

    @Test
    @DisplayName("handle debe crear el perfil cuando recibe UserRegistered")
    void shouldCreateProfileOnUserRegistered() throws Exception {
        var payload = new ObjectMapper().readTree("{\"userId\":\"u-1\",\"username\":\"ana\",\"email\":\"ana@urbanmove.es\",\"fullName\":\"Ana\"}");
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), "UserRegistered", 1,
                "urbanmove.identity", Instant.now(), Instant.now(), "corr-1", null, "user", "u-1", payload);

        controller.handle(envelope);

        verify(jdbc).update(contains("INSERT INTO user_profile"), eq("u-1"), eq("ana"), eq("ana@urbanmove.es"), eq("Ana"));
    }
}
