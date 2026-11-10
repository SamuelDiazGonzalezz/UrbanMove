---
name: java-testing-testcontainers
description: Pruebas unitarias modernas con JUnit 5, AssertJ, Mockito y pruebas de integración reales y efímeras con Testcontainers (PostgreSQL, Kafka, Redis, etc.) para Java.
---

# Java Modern Testing & Testcontainers

Esta skill orienta el desarrollo de una pirámide de pruebas robusta y realista, eliminando el uso de bases de datos H2 en memoria para integración (las cuales enmascaran diferencias de dialecto, funciones SQL y concurrencia real).

## 1. Stack Recomendado

- **Pruebas Unitarias**: JUnit 5 (`org.junit.jupiter`), AssertJ (`org.assertj.core.api.Assertions`).
- **Aislamiento**: Mockito (`@ExtendWith(MockitoExtension.class)`).
- **Pruebas de Integración**: Spring Boot Test (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) + **Testcontainers**.
- **Pruebas de Capa Web**: `@WebMvcTest` para verificar mapeo JSON, validaciones de beans y códigos HTTP de respuesta sin arrancar el contexto completo.

---

## 2. Testcontainers para Bases de Datos (PostgreSQL)

Desde Spring Boot 3.1+, la anotación `@ServiceConnection` autoconfigura `spring.datasource.*` a partir del contenedor en ejecución sin necesidad de declarar un `DynamicPropertySource`:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class VehicleRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void shouldPersistAndRetrieveVehicle() {
        // Given
        var vehicle = new VehicleEntity(UUID.randomUUID(), "BUS-042", VehicleType.BUS);

        // When
        vehicleRepository.save(vehicle);
        var found = vehicleRepository.findById(vehicle.getId());

        // Then
        assertThat(found)
                .isPresent()
                .hasValueSatisfying(v -> {
                    assertThat(v.getPlateNumber()).isEqualTo("BUS-042");
                    assertThat(v.getType()).isEqualTo(VehicleType.BUS);
                });
    }
}
```

---

## 3. Testcontainers para Apache Kafka

```java
@SpringBootTest
@Testcontainers
class TripEventKafkaConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Autowired
    private KafkaTemplate<String, TripCompletedEvent> kafkaTemplate;

    @Autowired
    private TripAnalyticsService tripAnalyticsService;

    @Test
    void shouldProcessTripCompletedEvent() {
        var event = new TripCompletedEvent("trip-999", "vehicle-12", 15.4, Instant.now());
        
        kafkaTemplate.send("trip-events", event.tripId(), event);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var stats = tripAnalyticsService.getStatsForVehicle("vehicle-12");
            assertThat(stats.totalKilometers()).isEqualTo(15.4);
        });
    }
}
```

---

## 4. Reglas Clave de Testing
1. **AssertJ Fluido**: Preferir `assertThat(actual).isEqualTo(expected)` a `assertEquals(expected, actual)`.
2. **Uso de `awaitility`**: Para pruebas asíncronas (Kafka, eventos en cola, tareas programadas), usar siempre `await().atMost(...).untilAsserted(...)` en lugar de `Thread.sleep()`.
3. **Contenedores Reutilizables (Singleton Container Pattern)**: Si la suite de pruebas crece, crear una clase base abstracta `AbstractIntegrationTest` que comparta los contenedores estáticos entre todos los tests para acelerar la ejecución total de la build.
