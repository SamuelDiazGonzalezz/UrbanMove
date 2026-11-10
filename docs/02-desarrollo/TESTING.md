# Estrategia de pruebas — UrbanMove

> **Ámbito:** los 13 microservicios y las Lambdas.
> **Objetivo:** que un fallo se detecte en segundos en local, no en la demo.
> **Documentos relacionados:** [CODE-STANDARDS.md](CODE-STANDARDS.md) (diseño),
> [LOCAL-DEV.md](LOCAL-DEV.md) (entorno y TDD), [CI-CD.md](../03-operacion/CI-CD.md) (pipeline).

---

## 1. Decisión: TDD selectivo

**Sí a test-first en dominio, contratos e idempotencia. No a TDD ceremonial en
configuración y *glue*.**

Con tres personas y dos meses, escribir primero el test de un `@Configuration` o de un
mapper trivial cuesta más de lo que protege. Pero hay zonas donde un error cuesta horas
de demo: la doble reserva, la máquina de estados, la idempotencia y la reconstrucción de
marts. Ahí el test se escribe **antes** que el código.

El flujo por caso de uso está en [LOCAL-DEV.md](LOCAL-DEV.md), sección 7.7.

---

## 2. Herramientas

| Herramienta | Versión | Papel |
|---|---|---|
| JUnit 5 | la del BOM de Spring Boot | Motor de pruebas |
| AssertJ | la del BOM | Aserciones fluidas |
| Mockito | la del BOM | Mocks de puertos |
| Testcontainers | 1.21.x | PostgreSQL, LocalStack y Keycloak reales |
| Awaitility | la del BOM | Esperas asíncronas |
| ArchUnit | 1.4.x | Reglas de arquitectura |
| JaCoCo | 0.8.13 | Cobertura |
| REST Assured / Postman+Newman | última | Flujos E2E |

Regla: **no se fijan versiones a mano** si el BOM de Spring Boot ya las gestiona.
Fijarlas por separado provoca conflictos de transitivas.

---

## 3. Pirámide

| Nivel | Herramienta | Cantidad | Momento |
|---|---|---|---|
| Contrato | JSON Schema + OpenAPI | 1 por evento/API | Antes del código |
| Dominio | JUnit 5 + AssertJ, sin Spring | Muchos | Test-first |
| Adaptadores | Testcontainers | Por repositorio y consumidor | Después, obligatorio |
| *Slice* web | `@WebMvcTest` | 1-2 por controlador | Después |
| Arquitectura | ArchUnit | 1 clase por servicio | Con el esqueleto |
| E2E | Postman/Newman o REST Assured | 5 flujos | Al cerrar fase |

La forma correcta es una pirámide, no un helado: muchos tests rápidos de dominio, pocos
tests lentos de integración y muy pocos E2E.

---

## 4. Pruebas de dominio (sin Spring)

El dominio es Java puro: los tests se ejecutan en milisegundos y no necesitan contexto.

```java
class ReservationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void no_permite_dos_reservas_activas_para_el_mismo_vehiculo() {
        VehicleId vehicleId = VehicleId.of("bici-42");
        Reservation.create(UserId.of("u1"), vehicleId, FIXED_CLOCK);

        assertThatThrownBy(() -> Reservation.create(UserId.of("u2"), vehicleId, FIXED_CLOCK))
                .isInstanceOf(VehicleAlreadyReservedException.class);
    }

    @Test
    void una_reserva_pendiente_expira_a_los_quince_minutos() {
        Reservation reservation = Reservation.create(UserId.of("u1"),
                VehicleId.of("bici-42"), FIXED_CLOCK);

        assertThat(reservation.isExpiredAt(FIXED_CLOCK.instant().plusSeconds(14 * 60))).isFalse();
        assertThat(reservation.isExpiredAt(FIXED_CLOCK.instant().plusSeconds(16 * 60))).isTrue();
    }
}
```

Reglas:

- Sin `@SpringBootTest`, sin `@Autowired`, sin base de datos.
- El tiempo se inyecta con `Clock`; **nunca** `Instant.now()` dentro de una regla.
- Se prueba comportamiento, no getters.
- Un test por transición de la máquina de estados y por regla de negocio.
- Los nombres describen la regla en lenguaje de negocio.

---

## 5. Pruebas de *slice* web

Para verificar JSON, validación y códigos HTTP sin arrancar el contexto completo.

```java
@WebMvcTest(VehicleAssetRestAdapter.class)
class VehicleAssetRestAdapterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterVehicleAssetUseCase registerVehicleAssetUseCase;

    @Test
    void devuelve_201_con_la_representacion_del_recurso() throws Exception {
        given(registerVehicleAssetUseCase.registerAsset(any()))
                .willReturn(VehicleAssetFixture.operable());

        mockMvc.perform(post("/api/v1/vehicle-assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licensePlate":"1234-ABC","vehicleModelCode":"BIKE-V2",
                                 "purchaseDate":"2026-01-10","fleetId":"f-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("OPERABLE"));
    }

    @Test
    void devuelve_400_con_ProblemDetail_si_falta_la_matricula() throws Exception {
        mockMvc.perform(post("/api/v1/vehicle-assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleModelCode\":\"BIKE-V2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"));
    }
}
```

No se prueba cada campo de cada DTO aquí: eso ya lo cubre el contrato OpenAPI.

---

## 6. Pruebas de persistencia con Testcontainers (PostgreSQL)

**Nunca H2.** H2 no reproduce `jsonb`, `ON CONFLICT`, `SKIP LOCKED`, índices parciales ni
la concurrencia real de PostgreSQL; un test en verde contra H2 no prueba el
comportamiento de producción.

```java
@SpringBootTest
@Testcontainers
class VehicleAssetRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private VehicleAssetRepositoryPort vehicleAssetRepository;

    @Test
    void persiste_y_recupera_un_activo_por_id() {
        VehicleAsset saved = vehicleAssetRepository.save(VehicleAssetFixture.operable());

        assertThat(vehicleAssetRepository.findById(saved.id()))
                .isPresent()
                .hasValueSatisfying(found -> {
                    assertThat(found.licensePlate()).isEqualTo(saved.licensePlate());
                    assertThat(found.status()).isEqualTo(VehicleStatus.OPERABLE);
                });
    }
}
```

Puntos importantes:

- `@ServiceConnection` autoconfigura el datasource; no hace falta `DynamicPropertySource`.
- Las migraciones **Flyway** reales se aplican en el contenedor: así se prueba también el
  esquema.
- Se prueba el adaptador, no Spring Data. El `save()` de Spring Data no necesita test.
- Los tests de repositorio deben incluir las consultas conflictivas: `SKIP LOCKED` del
  outbox, unicidad de reserva activa y `ON CONFLICT` de idempotencia.

### 6.1. Contenedor reutilizable

```java
@Testcontainers
public abstract class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withReuse(true);

    static {
        POSTGRES.start();
    }
}
```

El patrón *singleton container* comparte un único contenedor entre clases y reduce el
tiempo de la suite. En Windows conviene Docker Desktop con WSL2 y `withReuse(true)`.

---

## 7. Pruebas con LocalStack (S3, SQS y EventBridge)

Los eventos son una parte crítica del sistema: si el *payload* o el enrutado está mal, el
fallo aparece en otro servicio.

```java
@SpringBootTest
@Testcontainers
class ReservationConfirmedConsumerTest {

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
                    .withServices(LocalStackContainer.Service.S3,
                                  LocalStackContainer.Service.SQS);

    @DynamicPropertySource
    static void localStackProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.endpoint", LOCALSTACK::getEndpoint);
        registry.add("spring.cloud.aws.region.static", LOCALSTACK::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
    }

    @Autowired
    private SqsTemplate sqsTemplate;

    @Autowired
    private RideProjectionRepository rideProjectionRepository;

    @Test
    void procesa_el_evento_y_actualiza_la_proyeccion() {
        sqsTemplate.send("reservation-confirmed-queue",
                EventFixture.reservationConfirmed("reservation-1"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(rideProjectionRepository.existsByReservationId("reservation-1"))
                        .isTrue());
    }
}
```

Notas:

- La imagen se fija igual que en Compose (`4.14.0`); no `latest`.
- Si el módulo de Testcontainers no expone EventBridge, se declara con
  `.withEnv("SERVICES", "s3,sqs,events")` y se crea el cliente con el *endpoint* del
  contenedor.
- Los recursos (bucket, colas, bus, reglas y DLQ) se crean con un script de inicialización
  equivalente al `localstack-init` de Compose.
- Las pruebas de DLQ verifican que un mensaje inválido acaba en la cola de fallos y **no**
  se descarta.

---

## 8. Pruebas asíncronas con Awaitility

```java
// ❌ Incorrecto — lento y frágil
Thread.sleep(3000);
assertThat(repository.count()).isEqualTo(1);

// ✅ Correcto
await().atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> assertThat(repository.count()).isEqualTo(1));
```

Regla: `Thread.sleep()` está prohibido en tests. Awaitility falla con un mensaje claro y
no alarga la suite cuando la condición ya se cumple.

---

## 9. Contratos

### 9.1. REST

- El `openapi.yaml` se versiona en `contracts/<servicio>/`.
- Se valida que el contrato y la implementación no divergen (SpringDoc + comparación en
  CI, o tests de contrato).
- Un cambio incompatible obliga a subir la versión de API (`/api/v2`).

### 9.2. Eventos

- Cada tipo de evento tiene su **JSON Schema** en `contracts/events/`.
- El test valida que el evento serializado cumple el esquema.
- El sobre común (`eventId`, `eventType`, `occurredAt`, `correlationId`, `version`) es
  obligatorio y se comprueba en un test dedicado.
- Un cambio incompatible sube `version` del evento, no se muta el existente
  (ver [EVENTS.md](../01-arquitectura/EVENTS.md)).

### 9.3. Idempotencia

Un test explícito por consumidor:

```java
@Test
void procesar_dos_veces_el_mismo_evento_no_duplica_el_efecto() {
    ReservationConfirmedEvent event = EventFixture.reservationConfirmed("reservation-1");

    consumer.handle(event);
    consumer.handle(event);

    assertThat(rideProjectionRepository.countByReservationId("reservation-1")).isEqualTo(1);
}
```

---

## 10. Tests de arquitectura

Un test de ArchUnit por servicio, en el módulo de test, que falle en el PR si alguien
rompe la estructura:

```java
@AnalyzeClasses(packages = "com.urbanmove.reservations")
class ReservationsArchitectureTest {

    @ArchTest
    static final ArchRule domain_no_depende_de_frameworks =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                                        "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule adaptadores_no_se_cruzan =
            noClasses().that().resideInAPackage("..infrastructure.rest..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule casos_de_uso_no_dependen_de_controladores =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..");
}
```

---

## 11. Cobertura

| Ámbito | Objetivo | Comentario |
|---|---|---|
| Dominio | 70-80 % | Es donde están las reglas que importan |
| Global por servicio | 50 % | Umbral de CI, no meta |
| Configuración y DTOs | sin objetivo | Perseguir cobertura aquí produce tests vacíos |

JaCoCo genera el informe y el pipeline falla por debajo del umbral global. El 100 % no es
un objetivo: mide ejecución, no calidad.

---

## 12. Anti-patrones prohibidos

| Anti-patrón | Por qué |
|---|---|
| H2 en tests de integración | No reproduce PostgreSQL |
| `Thread.sleep()` | Lento y frágil |
| Tests sin aserciones | Pasan siempre, no prueban nada |
| Mockear el dominio | Se acaba probando el mock |
| Probar getters y records sin lógica | Ruido |
| Test E2E para cada campo | Duplica el contrato y ralentiza |
| `@SpringBootTest` para lógica pura | Arranca 10 s para probar un `if` |
| Cobertura alta con tests triviales | Falsa sensación de seguridad |
| Ignorar tests intermitentes | El siguiente fallo real se confunde con ruido |

---

## 13. Integración con CI

El pipeline (ver [CI-CD.md](../03-operacion/CI-CD.md)) ejecuta:

1. `./mvnw verify` con tests unitarios, de *slice* y de integración.
2. Testcontainers sobre los *runners* de GitHub con Docker disponible.
3. Informe JaCoCo y umbral mínimo.
4. Tests de arquitectura (ArchUnit).
5. Validación de contratos OpenAPI y JSON Schema.
6. Escaneo de seguridad (Trivy, gitleaks).

No se mergea un PR con tests rojos, tests intermitentes sin justificar o cobertura por
debajo del umbral.

---

## 14. Checklist de pruebas por servicio

- [ ] Reglas de dominio con tests unitarios y sin Spring.
- [ ] Un test por transición de estado.
- [ ] Un test de idempotencia por consumidor.
- [ ] Adaptador de persistencia con PostgreSQL real (Testcontainers).
- [ ] Consumidor/productor probado contra LocalStack.
- [ ] Contrato OpenAPI y *schemas* de eventos actualizados.
- [ ] Test de arquitectura con ArchUnit.
- [ ] Sin H2, sin `Thread.sleep()`, sin tests vacíos.
- [ ] Cobertura de dominio dentro del objetivo.
- [ ] `./mvnw verify` verde en local antes del PR.
