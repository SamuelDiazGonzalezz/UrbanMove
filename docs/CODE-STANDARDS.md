# Estándares de código Java — UrbanMove

> **Ámbito:** obligatorio en los 13 microservicios y en las Lambdas escritas en Java.
> **Objetivo:** que cualquier integrante pueda leer, revisar y modificar código de otro
> servicio sin contexto adicional.
> **Relación con otros documentos:** la arquitectura general está en
> [ARCHITECTURE.md](ARCHITECTURE.md), la estrategia de pruebas en [TESTING.md](TESTING.md)
> y el empaquetado en [CONTAINERS.md](CONTAINERS.md).

Estas reglas no son sugerencias de estilo. Se revisan en cada pull request y, cuando es
posible, se verifican automáticamente con ArchUnit.

---

## 1. Principios

1. **El código se lee más veces de las que se escribe.** El nombre manda; el comentario
   es el último recurso.
2. **Inmutable por defecto.** Todo lo que pueda ser `record` o `final`, lo es.
3. **El dominio no conoce frameworks.** Sin Spring, sin JPA, sin Jackson en `domain`.
4. **Un método, una responsabilidad, un nivel de abstracción.**
5. **Nada de nombres genéricos.** En un monorepo, `Vehicle` es una pregunta, no un nombre.
6. **Lo que no se puede comprobar, se degrada.** Las reglas importantes tienen un test de
   ArchUnit que las protege.

---

## 2. Nomenclatura

### 2.1. Clases y records — PascalCase con sustantivo de dominio

El nombre debe expresar **qué es** y a qué *bounded context* pertenece.

```java
// ✅ Correcto — concepto de dominio + contexto
public record VehicleAsset(VehicleAssetId id, LicensePlate licensePlate, VehicleStatus status) {}
public record TripFare(Money amount, FareCurrency currency) {}
public class RideSessionJpaEntity { /* ... */ }

// ❌ Incorrecto — ambiguo en un monorepo con 13 servicios
public class Vehicle {}      // ¿inventario, disponibilidad o viajes?
public class Entity {}       // opaco
public record Data() {}      // sin significado
```

### 2.2. Métodos — camelCase con verbo de intención

El nombre debe decir **qué hace** sin leer el cuerpo. Un verbo solo no es un nombre.

| ❌ Prohibido | ✅ Alternativa |
|---|---|
| `process()` | `processPaymentRefund()`, `applyFareDiscount()` |
| `handle()` | `handleVehicleBreakdownEvent()` |
| `manage()` | `manageFleetAvailability()` |
| `execute()` | `executeRouteAssignment()` |
| `run()` | `runPeriodicMaintenanceCheck()` |
| `doStuff()` | siempre incorrecto |

```java
// ✅ Correcto
VehicleAsset registerAsset(RegisterVehicleAssetCommand command);
void publishDomainEvent(VehiclePurchasedEvent event);
TripFare calculateTripFare(TripRoute route, FarePolicy policy);
Optional<VehicleAsset> findAvailableAssetNearLocation(GeoCoordinate location);

// ❌ Incorrecto
VehicleAsset process(Command command);
void handle(Object event);
TripFare calculate(Route route, Policy policy);
```

### 2.3. Variables — camelCase, sin abreviaturas

Se leen como prosa. Los sufijos de tipo (`dto`, `obj`, `res`) no aportan nada.

| ❌ Prohibido | ✅ Correcto |
|---|---|
| `svc` | `vehicleAssetService`, `fareCalculatorService` |
| `repo` | `vehicleAssetRepository` |
| `mgr` | `fleetAvailabilityManager` |
| `dto` | `vehicleAssetResponse`, `registerAssetRequest` |
| `obj`, `temp`, `res`, `rsp` | el nombre del concepto real |
| `e` en `catch` | `vehicleNotFoundException`, `fareCalculationException` |
| `i`, `j` | solo en bucles matemáticos puros; si no, `vehicleIndex` |

```java
// ✅ Correcto
private final VehicleAssetRepositoryPort vehicleAssetRepository;
private final EventPublisherPort eventPublisher;

// ❌ Incorrecto
private final VehicleAssetRepositoryPort repo;
private final EventPublisherPort pub;
```

### 2.4. Constantes — UPPER_SNAKE_CASE

```java
// ✅ Correcto
private static final int MAX_VEHICLE_CAPACITY_PASSENGERS = 4;
private static final String VEHICLE_EVENTS_TOPIC = "urbanmove.vehicle.events.v1";
private static final Duration ASSET_AVAILABILITY_CACHE_TTL = Duration.ofMinutes(5);
private static final BigDecimal LONG_TRIP_SURCHARGE_MULTIPLIER = new BigDecimal("1.15");

// ❌ Incorrecto
private static final int maxCapacity = 4;
private static final int MAGIC = 4;
```

### 2.5. Booleanos — prefijo `is`, `has`, `can`

```java
// ✅ Correcto
boolean isOperable();
boolean hasAvailableCapacity();
boolean canAcceptNewRideRequests();

// ❌ Incorrecto — verbos de acción, o validaciones que deberían lanzar excepción
boolean checkStatus();
boolean verifyCapacity();
boolean validateAsset();
```

### 2.6. Paquetes

Minúsculas, sin separadores, con el contexto del servicio explícito:

```text
com.urbanmove.<servicio>.<capa>.<subdominio>
```

```java
// ✅ Correcto
package com.urbanmove.inventory.domain.model;
package com.urbanmove.reservations.domain.port.in;
package com.urbanmove.rides.infrastructure.persistence;

// ❌ Incorrecto
package com.urbanmove.service;     // ¿qué servicio?
package com.urbanmove.repository;  // ¿qué repositorio?
package com.urbanmove.model;       // ambiguo entre servicios
package com.urbanmove.util;        // casi siempre hay un lugar más específico
```

### 2.7. Artefactos de la arquitectura hexagonal

| Artefacto | Sufijo | Ejemplo |
|---|---|---|
| Puerto de entrada (interfaz) | `UseCase` | `RegisterVehicleAssetUseCase` |
| Implementación del caso de uso | `UseCaseImpl` | `RegisterVehicleAssetUseCaseImpl` |
| Puerto de salida (interfaz) | `Port` | `VehicleAssetRepositoryPort` |
| Adaptador REST | `RestAdapter` | `VehicleAssetRestAdapter` |
| Adaptador de persistencia | `JpaRepositoryAdapter` | `VehicleAssetJpaRepositoryAdapter` |
| Repositorio Spring Data | `SpringDataJpaRepository` | `VehicleAssetSpringDataJpaRepository` |
| Adaptador de publicación de eventos | `EventBridgePublisherAdapter` | `VehicleEventBridgePublisherAdapter` |
| Consumidor SQS | `SqsConsumerAdapter` | `ReservationConfirmedSqsConsumerAdapter` |
| Entidad JPA | `JpaEntity` | `VehicleAssetJpaEntity` |
| Modelo de dominio | sin sufijo | `VehicleAsset`, `TripFare` |
| Comando | `Command` | `RegisterVehicleAssetCommand` |
| Evento de dominio | `Event` | `VehiclePurchasedEvent` |
| DTO de petición REST | `Request` | `RegisterVehicleAssetRequest` |
| DTO de respuesta REST | `Response` | `VehicleAssetResponse` |
| Mapper entre capas | `Mapper` con contexto | `VehicleAssetJpaEntityMapper` |
| Value Object | nombre del concepto | `LicensePlate`, `FleetId`, `TripFare` |

Reglas adicionales:

- Los puertos de entrada **nunca** llevan el sufijo `Service`.
- Los puertos de salida **nunca** se llaman `Repository` a secas ni llevan prefijo `I`.
- La implementación de un adaptador **nunca** se llama `Impl`: el sufijo debe decir la
  tecnología (`JpaRepositoryAdapter`, `EventBridgePublisherAdapter`).
- Una entidad JPA **nunca** se llama igual que el modelo de dominio.

### 2.8. Nombres genéricos prohibidos

| ❌ Genérico | Problema | ✅ Por contexto |
|---|---|---|
| `Vehicle` | ¿inventario, disponibilidad o viajes? | `VehicleAsset` (inventario), `AvailableResource` (disponibilidad) |
| `VehicleService` | ¿qué operaciones? ¿qué contexto? | `RegisterVehicleAssetUseCase` |
| `VehicleRepository` | sin sufijo `Port`, sin contexto | `VehicleAssetRepositoryPort` |
| `VehicleEntity` | ¿JPA? ¿dominio? | `VehicleAssetJpaEntity` |
| `VehicleMapper` | ¿de qué a qué? | `VehicleAssetJpaEntityMapper` |
| `EventService` | ¿publica, consume, qué eventos? | `VehicleEventBridgePublisherAdapter` |
| `UserService` | en un sistema de 13 servicios | `DriverProfileUseCase`, `PassengerRegistrationUseCase` |

### 2.9. Checklist de nombres

- [ ] Cada clase revela su concepto sin ambigüedad en el monorepo.
- [ ] Los métodos usan verbos concretos, no `process`, `handle`, `manage`.
- [ ] No hay abreviaturas (`repo`, `svc`, `dto`, `obj`, `temp`).
- [ ] Booleanos con `is`, `has` o `can`.
- [ ] Constantes en `UPPER_SNAKE_CASE` con nombre descriptivo.
- [ ] El paquete incluye el servicio o *bounded context*.
- [ ] Puertos de entrada `UseCase`, de salida `Port`.
- [ ] Entidades JPA con sufijo `JpaEntity`.
- [ ] Comandos `Command`, eventos `Event`, DTOs `Request`/`Response`.

---

## 3. Calidad y legibilidad

### 3.1. Métodos de 20 líneas como máximo

Si un método supera 20 líneas, está haciendo más de una cosa. Se extraen métodos
privados con nombres descriptivos.

```java
// ✅ Correcto — el método principal se lee como una receta
public VehicleAsset registerAsset(RegisterVehicleAssetCommand command) {
    validateNoDuplicateLicensePlate(command.licensePlate());
    VehicleAsset newAsset = buildNewVehicleAsset(command);
    VehicleAsset savedAsset = vehicleAssetRepository.save(newAsset);
    publishVehiclePurchasedEvent(savedAsset, command.fleetId());
    log.info("Vehicle asset registered id={}", savedAsset.id());
    return savedAsset;
}
```

> El límite es una señal, no una métrica de calidad. Un `switch` exhaustivo de 25 líneas
> puede ser perfectamente legible; una función de 15 líneas con tres niveles de anidación,
> no.

### 3.2. Máximo 3 parámetros

A partir de 4, se usa un `record` como objeto de parámetros.

```java
// ❌ Incorrecto — 6 parámetros, el orden es una trampa
VehicleAsset createAsset(String licensePlate, String modelCode, LocalDate purchaseDate,
                         UUID fleetId, String color, int seatCapacity);

// ✅ Correcto
public record RegisterVehicleAssetCommand(
        LicensePlate licensePlate,
        VehicleModelCode vehicleModelCode,
        LocalDate purchaseDate,
        FleetId fleetId,
        VehicleColor color,
        SeatCapacity seatCapacity) {}

VehicleAsset registerAsset(RegisterVehicleAssetCommand command);
```

Para búsquedas con muchos filtros, un `record` de criterios con fábricas estáticas:

```java
public record VehicleAssetSearchCriteria(
        Optional<VehicleStatus> status,
        Optional<FleetId> fleetId,
        Optional<LocalDate> purchasedAfter,
        boolean includeRetired) {

    public static VehicleAssetSearchCriteria onlyOperable(FleetId fleetId) {
        return new VehicleAssetSearchCriteria(
                Optional.of(VehicleStatus.OPERABLE), Optional.of(fleetId),
                Optional.empty(), false);
    }
}
```

### 3.3. Comentarios

- **No** se comenta *qué* hace el código; para eso está el nombre.
- **Sí** JavaDoc en la API pública (interfaces de puertos y casos de uso): contrato,
  parámetros, retorno y excepciones.
- **Sí** comentarios de *por qué* ante decisiones no obvias (un lock pesimista, un
  *timeout* concreto, una limitación de LocalStack), con referencia al ADR si existe.

```java
// ❌ Incorrecto — repite el código
// Busca el vehículo por id
Optional<VehicleAsset> asset = vehicleAssetRepository.findById(assetId);

// ✅ Correcto — explica una decisión
// Bloqueo pesimista: dos retiradas concurrentes del mismo activo deben serializarse.
// Con bloqueo optimista aparecían RetryException en picos (ver ADR-012).
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<VehicleAssetJpaEntity> findByIdForUpdate(UUID assetId);
```

### 3.4. Cero *magic numbers* y *magic strings*

Todo literal con significado de negocio es una constante con nombre.

```java
// ❌ Incorrecto
if (tripDurationMinutes > 120) {
    fare = fare.multiply(new BigDecimal("1.15"));
}

// ✅ Correcto
private static final int LONG_TRIP_THRESHOLD_MINUTES = 120;
private static final BigDecimal LONG_TRIP_SURCHARGE_MULTIPLIER = new BigDecimal("1.15");

if (tripDurationMinutes > LONG_TRIP_THRESHOLD_MINUTES) {
    fare = fare.multiply(LONG_TRIP_SURCHARGE_MULTIPLIER);
}
```

Los nombres de cola, *bus*, bucket y prefijos S3 también son constantes o propiedades de
configuración, nunca literales dispersos.

### 3.5. Un solo nivel de abstracción por método

No se mezcla lógica de negocio con JPA, SQS o logging en el mismo cuerpo.

```java
// ✅ Correcto — el caso de uso habla solo de negocio
public void retireAssetPermanently(VehicleAssetId assetId, RetirementReason reason) {
    VehicleAsset asset = findOperableAssetOrThrow(assetId);
    ensureAssetHasNoActiveRides(assetId);
    VehicleAsset retiredAsset = asset.retire(reason);
    vehicleAssetRepository.save(retiredAsset);
    publishVehicleRetiredEvent(retiredAsset, reason);
}
```

Los detalles técnicos viven en el adaptador correspondiente, no en el caso de uso.

### 3.6. `Optional` con reglas

| Regla | ❌ Violación | ✅ Correcto |
|---|---|---|
| No se pasa como parámetro | `findAsset(Optional<UUID> id)` | `findAsset(UUID id)` |
| Nunca es `null` | `return null;` | `return Optional.empty();` |
| No se usa `.get()` a secas | `result.get()` | `.orElseThrow(() -> new VehicleAssetNotFoundException(id))` |
| No para colecciones | `Optional<List<T>>` | `List<T>` vacía |
| No como campo | `private Optional<String> name;` | `private String name;` |

```java
// ✅ Correcto — encadenado y con excepción de dominio
public TripFare calculateFareForNearbyAsset(GeoCoordinate pickup, TripRoute route) {
    return vehicleAssetRepository.findNearestOperableAsset(pickup)
            .map(asset -> tripFareCalculator.calculateTripFare(route, asset.farePolicy()))
            .orElseThrow(() -> new NoAvailableAssetNearLocationException(pickup));
}
```

### 3.7. Guard clauses frente a *arrow code*

Se falla rápido y se deja el camino feliz sin anidar.

```java
// ✅ Correcto
public VehicleAsset registerAsset(RegisterVehicleAssetCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(command.licensePlate(), "licensePlate must not be null");

    if (vehicleAssetRepository.existsByLicensePlate(command.licensePlate())) {
        throw new VehicleAssetAlreadyExistsException(command.licensePlate());
    }

    return vehicleAssetRepository.save(buildNewVehicleAsset(command));
}
```

### 3.8. Pattern matching en lugar de cadenas de `instanceof`

```java
public sealed interface VehicleStatus
        permits VehicleStatus.Parked, VehicleStatus.InTransit, VehicleStatus.Maintenance {

    record Parked(Coordinate location, Instant since) implements VehicleStatus {}
    record InTransit(Coordinate current, double speedKmH, String routeId) implements VehicleStatus {}
    record Maintenance(String reason, Instant estimatedReturn) implements VehicleStatus {}
}

// ✅ El compilador exige cubrir todos los casos
public String describe(VehicleStatus status) {
    return switch (status) {
        case VehicleStatus.Parked parked -> "Aparcado en " + parked.location();
        case VehicleStatus.InTransit inTransit -> "En tránsito a " + inTransit.speedKmH() + " km/h";
        case VehicleStatus.Maintenance maintenance -> "En mantenimiento: " + maintenance.reason();
    };
}
```

Ventaja real: al añadir un caso nuevo a la `sealed interface`, **el compilador rompe** y
obliga a revisar todos los usos. Con `instanceof` el fallo aparece en producción.

### 3.9. Logging estructurado

| Nivel | Uso |
|---|---|
| `DEBUG` | Trazas internas y valores intermedios |
| `INFO` | Eventos de negocio (reserva creada, evento publicado) |
| `WARN` | Situación recuperable (reintento, caché sin entrada) |
| `ERROR` | Fallo que requiere intervención |

```java
// ❌ Incorrecto — concatenación (se evalúa siempre) y sin contexto
log.info("Asset " + assetId + " registered");
log.error("Error: " + exception.getMessage());

// ✅ Correcto — parámetros lazy y campos con nombre
log.info("Vehicle asset registered assetId={} licensePlate={}", asset.id(), asset.licensePlate());
log.error("Failed to publish VehiclePurchasedEvent assetId={}", assetId, exception);
```

Reglas:

- Siempre SLF4J con `{}`; nunca `System.out` ni concatenación.
- El último argumento puede ser un `Throwable` para incluir el *stack trace*.
- Nunca se registran datos personales, tokens ni el *payload* completo de un evento.
- `correlationId` en MDC y propagado por cabecera y por el sobre del evento
  (ver [EVENTS.md](EVENTS.md)).
- Limpiar el MDC en `finally`: los hilos se reutilizan.

### 3.10. Streams y *fluent API*

Los pipelines de `Stream` son declarativos y sin efectos laterales. No se encadena lógica
de negocio con efectos (persistir, publicar, enviar) como si fuera un *builder*.

```java
// ✅ Correcto
Map<VehicleStatus, Long> countByStatus = assets.stream()
        .collect(Collectors.groupingBy(VehicleAsset::status, Collectors.counting()));

List<VehicleAsset> operableAssets = assets.stream()
        .filter(VehicleAsset::isOperable)
        .toList();   // lista inmodificable desde Java 16

// ❌ Incorrecto — el orden real de ejecución no es evidente
return assetBuilder.validate().enrich().persist().publishEvent().toResponse();
```

### 3.11. Builder solo cuando aporta

Los `record` no necesitan Builder: su constructor canónico es explícito. Se usa Builder
para clases mutables (entidades JPA, informes) con muchos campos opcionales.

```java
VehicleFleetReport report = VehicleFleetReport.builder()
        .fleetId(fleetId)
        .reportDate(LocalDate.now())
        .totalAssetCount(assets.size())
        .operableAssetCount(operableAssets.size())
        .build();
```

### 3.12. Checklist de legibilidad

- [ ] Ningún método supera ~20 líneas sin justificación.
- [ ] Ningún método tiene más de 3 parámetros o usa un `Command`/criterios.
- [ ] No hay comentarios que repitan el código.
- [ ] Los literales de negocio son constantes con nombre.
- [ ] Cada método opera en un único nivel de abstracción.
- [ ] `Optional` solo como retorno y nunca `null`.
- [ ] Guard clauses en lugar de pirámides de `if`.
- [ ] `switch` con pattern matching sobre tipos sellados.
- [ ] Logging con `{}` y niveles correctos.
- [ ] Streams declarativos y sin efectos laterales.

---

## 4. SOLID, Ley de Deméter y composición

### 4.1. Responsabilidad única (SRP)

Un caso de uso atómico por operación, no un servicio con 20 métodos.

```java
// ❌ Incorrecto
public interface VehicleService {
    VehicleAsset register(...);
    void retire(...);
    VehicleAsset update(...);
    List<VehicleAsset> findAll(...);
    void assignToFleet(...);
}

// ✅ Correcto
public interface RegisterVehicleAssetUseCase { VehicleAsset registerAsset(RegisterVehicleAssetCommand command); }
public interface RetireVehicleAssetUseCase { void retireAssetPermanently(VehicleAssetId assetId, RetirementReason reason); }
public interface GetVehicleAssetUseCase { VehicleAsset getAssetById(VehicleAssetId assetId); }
```

También se separan DTO de entrada, DTO de salida, modelo de dominio y entidad de
persistencia. Un `record` no hace de tres cosas a la vez.

### 4.2. Abierto/cerrado (OCP)

Se extiende con polimorfismo, `sealed interface` y `record`, no modificando un `switch`
central cada vez que aparece un caso.

### 4.3. Sustitución de Liskov (LSP)

Una implementación cumple el contrato completo del puerto. Si un método no aplica, el
diseño está mal: se parte la interfaz (ISP), no se lanza
`UnsupportedOperationException`.

### 4.4. Segregación de interfaces (ISP)

Puertos pequeños y cohesivos: `SaveVehicleAssetPort`, `FindVehicleAssetPort`,
`PublishVehicleEventPort`. Un cliente no debe depender de métodos que no usa.

### 4.5. Inversión de dependencias (DIP)

```text
infrastructure  ──►  application  ──►  domain
     (adapters)         (use cases)      (modelo y puertos)
```

El dominio define el puerto; la infraestructura lo implementa. Nunca al revés.

### 4.6. Ley de Deméter y *Tell, Don't Ask*

Un método solo puede llamar a:

1. Métodos de su propia clase.
2. Objetos recibidos como parámetro.
3. Objetos que crea él mismo.
4. Dependencias inyectadas (`private final`).

```java
// ❌ Violación — tren de invocaciones, acoplado a la estructura interna de 4 objetos
String city = vehicle.getLocation().getCoordinates().getCity().getName();

// ✅ Correcto — se pregunta al objeto raíz
boolean isInZone = vehicle.isLocatedInZone(zoneId);
```

Regla práctica: si ves más de **un punto** en una expresión, pregúntate si estás
pidiendo datos para decidir por otro objeto en lugar de decirle qué hacer.

### 4.7. Composición sobre herencia

- Prohibido `BaseEntity`, `BaseService`, `AbstractCrudController`, `GenericManager`.
- Contratos con **interfaces**; comportamiento compartido por **composición**.
- Variantes cerradas con **`sealed interface` + `record`**.

La herencia de clases base produce el *fragile base class problem*: un cambio en la base
rompe servicios que no lo esperaban y el acoplamiento no se ve en el diff.

---

## 5. Inmutabilidad y Java moderno

### 5.1. Inmutable por defecto

- DTOs, mensajes de evento, comandos y *value objects*: **siempre** `record`.
- Entidades de dominio sin identidad mutable: `record`.
- Entidades JPA: mutabilidad encapsulada en métodos de negocio; **sin setters** públicos.
- Campos `final` salvo imposibilidad técnica.
- Servicios `@Service` y controladores **stateless**: dependencias en `private final` por
  constructor.

### 5.2. Validación en el constructor compacto

```java
public record Coordinate(double latitude, double longitude) {
    public Coordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitud fuera de rango: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitud fuera de rango: " + longitude);
        }
    }
}
```

Un `record` inválido no debería poder existir. La validación va en el constructor, no en
un método `validate()` que alguien puede olvidar llamar.

### 5.3. Colecciones inmodificables y copia defensiva

```java
public record FleetGroup(FleetId fleetId, List<VehicleAsset> vehicles) {
    public FleetGroup {
        vehicles = List.copyOf(vehicles);   // copia inmutable
    }
}
```

- `List.of(...)`, `Set.of(...)`, `Map.of(...)` para colecciones fijas.
- `.toList()` en streams, no `Collectors.toList()`.
- Nunca se devuelve la referencia interna de una colección mutable.

### 5.4. Tipos algebraicos para estados

Los estados excluyentes del dominio se modelan con `sealed interface` y `record`, no con
un `enum` más un campo `String reason` que a veces aplica y a veces no.

```java
public sealed interface ReservationState
        permits ReservationState.Pending, ReservationState.Confirmed,
                ReservationState.Cancelled, ReservationState.Expired {

    record Pending(Instant expiresAt) implements ReservationState {}
    record Confirmed(Instant confirmedAt) implements ReservationState {}
    record Cancelled(String reason, Instant cancelledAt) implements ReservationState {}
    record Expired(Instant expiredAt) implements ReservationState {}
}
```

Esto permite `switch` exhaustivo y hace imposible representar combinaciones inválidas.

### 5.5. Verificación con ArchUnit

```java
@AnalyzeClasses(packages = "com.urbanmove.inventory")
class InventoryArchitectureTest {

    @ArchTest
    static final ArchRule domain_has_no_framework_dependencies =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                                        "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule domain_has_no_setters =
            noMethods().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..")
                    .should().haveNameStartingWith("set");
}
```

---

## 6. Estructura hexagonal por servicio

```text
com.urbanmove.<servicio>/
├── domain/                          # Núcleo puro, sin frameworks
│   ├── model/                       # Entidades, Value Objects, sealed interfaces
│   ├── event/                       # Eventos de dominio (records)
│   ├── exception/                   # Excepciones de dominio
│   └── port/
│       ├── in/                      # Casos de uso (interfaces)
│       └── out/                     # Puertos de salida
├── application/
│   └── usecase/                     # Implementación de los casos de uso
└── infrastructure/
    ├── rest/                        # Controladores, Request/Response, mappers
    ├── persistence/                 # JpaEntity, Spring Data, adaptadores, mappers
    ├── messaging/                   # Productores EventBridge, consumidores SQS, outbox
    └── config/                      # Beans de configuración (solo aquí hay @Configuration)
```

Reglas de dependencia:

| Regla | Verificación |
|---|---|
| `domain` no importa Spring, JPA ni Jackson | ArchUnit |
| `application` solo depende de `domain` | ArchUnit |
| `infrastructure` depende de `application` y `domain` | Revisión + ArchUnit |
| Un servicio no lee la base de otro | Revisión de repositorios y esquema |
| Los mappers viven en infraestructura, no en el dominio | Revisión |

### 6.1. Transacciones

- `@Transactional` en el **caso de uso**, no en el controlador ni en el adaptador.
- El cambio de negocio y el `INSERT` en `outbox_event` van en la **misma transacción**
  (ver [EVENTS.md](EVENTS.md)).
- El relay del outbox corre fuera de la transacción de negocio.

### 6.2. Mapeo entre capas

- Dominio ↔ JPA: mapper explícito (`VehicleAssetJpaEntityMapper`).
- Dominio ↔ REST: mapper explícito (`VehicleAssetResponseMapper`) o método estático del
  `Response`.
- Nunca se expone una entidad JPA en un `Response`.
- Nunca se anota el modelo de dominio con `@Entity` ni con `@Schema`.

---

## 7. APIs REST y errores

### 7.1. RFC 7807 (`ProblemDetail`)

Todos los errores usan el formato estándar; nada de `{"error": "...", "code": 42}`.

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(VehicleAssetNotFoundException.class)
    public ProblemDetail handleNotFound(VehicleAssetNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Recurso no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
```

Reglas:

- `detail` es un mensaje para el cliente, sin trazas ni SQL.
- Se pueden añadir propiedades (`invalidParams`, `correlationId`), no se cambia la forma
  básica.
- Los errores de validación devuelven 400 con el mapa de campos inválidos.
- Nunca se devuelve 200 con un cuerpo de error.

### 7.2. OpenAPI

- Cada servicio expone su contrato en `contracts/<servicio>/openapi.yaml` y en
  `/v3/api-docs`.
- Los DTOs llevan `@Schema(description = ..., example = ...)`.
- Cada operación declara sus respuestas reales (`200`, `201`, `400`, `404`, `409`, `503`).
- El contrato se actualiza **antes** o **con** el código, nunca después.

### 7.3. Convenciones de API

| Aspecto | Regla |
|---|---|
| Versionado | Prefijo `/api/v1` |
| Recursos | Sustantivos en plural, `kebab-case` |
| Verbos | Solo los de HTTP; nada de `/getVehicle` |
| Paginación | `page`, `size`, `sort`; respuesta con metadatos |
| Fechas | ISO-8601 en UTC (`Instant`) |
| Idempotencia | `Idempotency-Key` en operaciones sensibles |
| Correlación | Cabecera `X-Correlation-Id` propagada al evento |

---

## 8. Resiliencia

### 8.1. Regla general

Toda llamada de red (otro servicio, Keycloak, S3, SQS, EventBridge) tiene:

- **timeout** explícito,
- **reintento** solo si la operación es idempotente,
- **circuit breaker** si el fallo puede propagarse en cascada,
- **fallback** con significado de negocio, no un `null`.

### 8.2. Resiliencia4j

```java
@CircuitBreaker(name = "identityService", fallbackMethod = "fallbackProfile")
@Retry(name = "identityService")
public DriverProfile fetchDriverProfile(DriverId driverId) {
    return identityClient.get("/api/v1/drivers/{id}", driverId);
}

DriverProfile fallbackProfile(DriverId driverId, Throwable failure) {
    log.warn("Identity no disponible, usando perfil mínimo driverId={}", driverId, failure);
    return DriverProfile.minimal(driverId);
}
```

Configuración de referencia (ajustable por servicio):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      identityService:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
  retry:
    instances:
      identityService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff:
          multiplier: 2
```

### 8.3. Eventos

- Publicación **transaccional** vía outbox; nunca `send()` directo dentro del caso de uso.
- Consumo **idempotente**: el mismo `eventId` dos veces produce el mismo estado.
- Fallo de consumo: reintento de SQS y, tras el máximo, DLQ. No se descarta en silencio.
- El detalle está en [EVENTS.md](EVENTS.md).

### 8.4. Arranque tolerante

Un servicio debe arrancar aunque Keycloak o SQS no estén disponibles: conexión perezosa,
reintentos y `readiness` en falso hasta que las dependencias respondan. Si necesita el
orden de arranque para no romperse, hay un acoplamiento escondido (ver
[LOCAL-DEV.md](LOCAL-DEV.md), sección 6.2).

---

## 9. Checklist de revisión de código

Antes de pedir revisión:

- [ ] Los nombres son específicos del contexto y no hay genéricos.
- [ ] Métodos ≤ 20 líneas, ≤ 3 parámetros, un nivel de abstracción.
- [ ] Sin *magic numbers*, sin abreviaturas, sin comentarios redundantes.
- [ ] `Optional` y guard clauses usados correctamente.
- [ ] `sealed` + `switch` exhaustivo donde hay estados excluyentes.
- [ ] Dominio sin Spring, JPA ni Jackson.
- [ ] Inmutabilidad: `record`, `final`, colecciones copiadas.
- [ ] Un solo caso de uso por operación; puertos pequeños.
- [ ] Sin herencia de clases base ni setters en dominio.
- [ ] Errores con `ProblemDetail`; contrato OpenAPI actualizado.
- [ ] Timeouts, reintentos y circuit breaker en llamadas de red.
- [ ] Logs con `{}`, sin datos personales y con `correlationId`.
- [ ] El outbox se escribe en la misma transacción que el cambio de negocio.

---

## 10. Cómo se aplica

| Mecanismo | Qué cubre | Estado |
|---|---|---|
| Revisión de PR | Todo el documento | Desde el primer servicio |
| ArchUnit | Dependencias del dominio, setters, paquetes | Fase 1 |
| JaCoCo | Cobertura mínima por servicio | Fase 1 |
| OpenAPI + Schemas | Contratos REST y de eventos | Fase 1 |
| Checkstyle / Spotless | Formato e imports (opcional) | A valorar |

Las reglas de este documento forman parte de la definición de "hecho" de cada servicio
(ver [LOCAL-DEV.md](LOCAL-DEV.md), sección 9.1).
