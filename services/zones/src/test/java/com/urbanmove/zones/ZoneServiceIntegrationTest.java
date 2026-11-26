package com.urbanmove.zones;

import com.urbanmove.common.events.EventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/urbanmove/protocol/openid-connect/certs"
})
@Testcontainers(disabledWithoutDocker = true)
class ZoneServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    private EventBridgeClient eventBridgeClient;

    @MockBean
    private SqsClient sqsClient;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Debe persistir zonas reales en PostgreSQL y registrar ZoneCreated en outbox")
    void shouldPersistZoneAndOutboxInPostgres() {
        ZoneService.Zone created = zoneService.create(
                new ZoneService.NewZone("puerto-luz", "Puerto de la Luz", 28.1480, -15.4200, 500, 5));

        assertThat(created.zoneId()).isEqualTo("puerto-luz");
        Integer outboxCount = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'ZoneCreated' AND entity_id = 'puerto-luz'",
                Integer.class);
        assertThat(outboxCount).isEqualTo(1);
    }
}
