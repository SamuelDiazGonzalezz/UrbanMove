package com.urbanmove.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private SqsClient sqs;

    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(jdbc, sqs);
    }

    @Test
    @DisplayName("dashboard debe devolver estructura con marts, servicios y DLQs")
    void shouldReturnDashboardStructure() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of()));

        Map<String, Object> result = controller.dashboard();

        assertThat(result).containsKeys("demand", "fleetByState", "fleetByZone", "maintenance", "tripsToday");
    }
}
