package com.urbanmove.common.config;

import com.urbanmove.common.security.CurrentUser;
import com.urbanmove.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.time.Duration;

@Configuration
@EnableScheduling
public class PlatformConfig {

    @Value("${urbanmove.aws.endpoint:http://localstack:4566}")
    private String endpoint;

    @Value("${urbanmove.aws.region:eu-west-1}")
    private String region;

    @Value("${urbanmove.aws.access-key:test}")
    private String accessKey;

    @Value("${urbanmove.aws.secret-key:test}")
    private String secretKey;

    @Bean
    EventBridgeClient eventBridgeClient() {
        return EventBridgeClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Bean
    SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    /**
     * Cliente REST para llamadas síncronas entre servicios: timeouts cortos y propagación de
     * {@code X-Correlation-Id} y del token del usuario.
     */
    @Bean
    RestClient.Builder serviceRestClientBuilder() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    }
                    try {
                        String bearer = CurrentUser.bearer();
                        if (bearer != null && !request.getHeaders().containsKey("Authorization")) {
                            request.getHeaders().set("Authorization", bearer);
                        }
                    } catch (RuntimeException ignored) {
                        // sin usuario autenticado (p. ej. consumidor de eventos)
                    }
                    return execution.execute(request, body);
                });
    }
}
