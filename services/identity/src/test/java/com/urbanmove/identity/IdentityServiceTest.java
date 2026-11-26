package com.urbanmove.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private KeycloakClient keycloak;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private EventStore events;

    @Mock
    private TransactionTemplate tx;

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(keycloak, jdbc, events, tx, new ObjectMapper());
    }

    @Test
    @DisplayName("login debe rechazar credenciales nulas")
    void shouldRejectNullCredentials() {
        assertThatThrownBy(() -> service.login(null, "secret"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("obligatorios");

        assertThatThrownBy(() -> service.login("ana", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("obligatorios");
    }
}
