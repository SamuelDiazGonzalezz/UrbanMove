---
name: java-naming-conventions
description: >
  Convenciones de nomenclatura Java para el proyecto UrbanMove en entornos multimicroservicio.
  Define reglas estrictas de nombrado para clases, métodos, variables, paquetes y artefactos
  de arquitectura hexagonal (UseCases, Ports, Adapters, Commands, Events, DTOs, Entities).
  Actívala siempre que se genere o revise código Java en cualquier microservicio de UrbanMove.
---

# Java Naming Conventions — UrbanMove

> Estas convenciones son **obligatorias** en todos los microservicios del monorepo UrbanMove.
> El objetivo es que cualquier desarrollador pueda navegar el código sin necesidad de documentación adicional.

---

## 1. Reglas Generales del Lenguaje

### 1.1 Clases — PascalCase con sustantivo descriptivo

La clase debe expresar **qué es**, no qué hace. Incluye siempre el contexto del bounded context.

```java
// ✅ Correcto — el nombre revela el concepto de dominio y el contexto
public record VehicleAsset(VehicleAssetId id, LicensePlate licensePlate, VehicleStatus status) {}
public record TripFare(Money amount, FareCurrency currency) {}
public class RideSessionJpaEntity { ... }

// ❌ Incorrecto — genérico, ambiguo en un monorepo
public record Vehicle(...) {}     // ¿del bounded context de inventario, disponibilidad o viajes?
public class Entity(...) {}       // totalmente opaco
public record Data(...) {}        // sin ningún significado
```

### 1.2 Métodos — camelCase con verbo de intención clara

El nombre del método debe revelar **qué hace** sin necesidad de leer su cuerpo.

**Verbos prohibidos como nombre único de método:**

| ❌ Prohibido | ✅ Alternativa correcta |
|---|---|
| `process()` | `processPaymentRefund()`, `applyFareDiscount()` |
| `handle()` | `handleVehicleBreakdownEvent()` |
| `doStuff()` | (siempre incorrecto) |
| `manage()` | `manageFleetAvailability()` |
| `execute()` solo | `executeRouteAssignment()` |
| `run()` solo | `runPeriodicMaintenanceCheck()` |

```java
// ✅ Correcto — verbos con intención clara
public VehicleAsset registerAsset(RegisterVehicleAssetCommand command) { ... }
public void publishDomainEvent(VehiclePurchasedEvent event) { ... }
public TripFare calculateTripFare(TripRoute route, FarePolicy policy) { ... }
public Optional<VehicleAsset> findAvailableAssetNearLocation(GeoCoordinate location) { ... }
public void retireAssetPermanently(VehicleAssetId assetId, RetirementReason reason) { ... }

// ❌ Incorrecto — opacos, requieren leer la implementación para entender el propósito
public VehicleAsset process(Command command) { ... }
public void handle(Object event) { ... }
public void doStuff() { ... }
public TripFare calculate(Route r, Policy p) { ... }
```

### 1.3 Variables — camelCase sin abreviaciones

Cada variable debe leerse como prosa: **sin abreviaciones, sin sufijos genéricos**.

**Nombres prohibidos:**

| ❌ Prohibido | Motivo | ✅ Correcto |
|---|---|---|
| `svc` | abreviación de service | `vehicleAssetService`, `fareCalculatorService` |
| `mgr` | abreviación de manager | `fleetAvailabilityManager` |
| `repo` | abreviación de repository | `vehicleAssetRepository` |
| `dto` | tipo de artefacto sin contexto | `vehicleAssetResponse`, `registerAssetRequest` |
| `obj` | sin ningún significado | nombre del concepto real |
| `temp`, `tmp` | variable sin propósito claro | nombre descriptivo del dato temporal |
| `res`, `rsp` | abreviación de result/response | `vehicleAssetResponse`, `tripFareResult` |
| `e` en catch | excepciones deben nombrarse | `vehicleNotFoundException`, `fareCalculationException` |
| `i`, `j` (loops) | solo aceptables en bucles matemáticos puros | `vehicleIndex`, `fareSegmentIndex` |

```java
// ✅ Correcto
private final VehicleAssetRepositoryPort vehicleAssetRepository;
private final EventPublisherPort eventPublisher;
private final TripFareCalculator tripFareCalculator;

VehicleAsset registeredAsset = vehicleAssetRepository.save(newAsset);
Optional<VehicleAsset> nearbyAsset = vehicleAssetRepository.findNearLocation(coordinates);

// ❌ Incorrecto
private final VehicleAssetRepositoryPort repo;
private final EventPublisherPort pub;
private final TripFareCalculator calc;

VehicleAsset res = repo.save(obj);
Optional<VehicleAsset> temp = repo.find(coord);
```

### 1.4 Constantes — UPPER_SNAKE_CASE

```java
// ✅ Correcto
public static final int MAX_VEHICLE_CAPACITY_PASSENGERS = 4;
public static final String DEFAULT_FARE_CURRENCY_CODE = "EUR";
public static final Duration ASSET_AVAILABILITY_CACHE_TTL = Duration.ofMinutes(5);
public static final BigDecimal MINIMUM_TRIP_FARE_AMOUNT = new BigDecimal("1.50");

// ❌ Incorrecto
private static final int maxCapacity = 4;       // camelCase para constante
private static final String currency = "EUR";   // sin UPPER_SNAKE, sin contexto
private static final int MAGIC = 4;             // nombre sin significado
```

### 1.5 Booleanos descriptivos — prefijo `is`, `has`, `can`

```java
// ✅ Correcto
boolean isOperable()              // ¿el vehículo está operativo?
boolean isPermanentlyRetired()    // ¿el activo ha sido retirado definitivamente?
boolean hasAvailableCapacity()    // ¿quedan plazas libres?
boolean canAcceptNewRideRequests() // ¿el conductor puede aceptar viajes?

// ❌ Incorrecto — verbos de acción como nombre de predicado
boolean checkStatus()       // "check" implica una acción, no un estado
boolean verifyCapacity()    // "verify" es imperativo, no predicativo
boolean validateAsset()     // debería lanzar excepción si inválido, no devolver boolean
```

---

## 2. Nomenclatura de Paquetes

Los paquetes van en **lowercase sin separadores** y deben expresar el **bounded context** explícitamente.

```
com.urbanmove.<microservicio>.<capa>.<subdominio>
```

### Estructura de paquetes por microservicio

```
com.urbanmove.inventory/              ← microservicio de inventario de vehículos
  ├── domain/
  │   ├── model/                      ← VehicleAsset, VehicleStatus, LicensePlate
  │   ├── event/                      ← VehiclePurchasedEvent, VehicleRetiredEvent
  │   └── port/
  │       ├── in/                     ← RegisterVehicleAssetUseCase, GetVehicleAssetUseCase
  │       └── out/                    ← VehicleAssetRepositoryPort, EventPublisherPort
  ├── application/
  │   └── usecase/                    ← RegisterVehicleAssetUseCaseImpl
  └── infrastructure/
      ├── persistence/                ← VehicleAssetJpaEntity, VehicleAssetJpaRepositoryAdapter
      ├── messaging/                  ← VehicleEventKafkaPublisherAdapter
      └── rest/                       ← VehicleAssetRestAdapter, RegisterVehicleAssetRequest

com.urbanmove.availability/           ← microservicio de disponibilidad
  └── domain/model/                   ← AvailableResource (NO VehicleAsset)

com.urbanmove.rides/                  ← microservicio de viajes
  └── domain/model/                   ← RideSession (NO Vehicle, NO VehicleAsset)
```

```java
// ✅ Correcto — contexto explícito en el paquete
package com.urbanmove.inventory.domain.model;
package com.urbanmove.inventory.domain.port.in;
package com.urbanmove.rides.infrastructure.persistence;

// ❌ Incorrecto — paquetes genéricos que pierden el contexto en el monorepo
package com.urbanmove.service;        // ¿qué servicio?
package com.urbanmove.repository;     // ¿qué repositorio?
package com.urbanmove.model;          // ambiguo entre microservicios
package com.urbanmove.util;           // siempre hay un lugar más específico
```

---

## 3. Nomenclatura de Artefactos de Arquitectura Hexagonal

### 3.1 Casos de Uso (Puertos de Entrada)

**Sufijo:** `UseCase`  
**Convención:** `<Verbo><Concepto>UseCase`  
**NUNCA** usar el sufijo `Service` para los puertos del dominio.

```java
// ✅ Correcto — la interfaz del puerto de entrada
public interface RegisterVehicleAssetUseCase {
    VehicleAsset registerAsset(RegisterVehicleAssetCommand command);
}

public interface GetVehicleAssetUseCase {
    VehicleAsset getAssetById(VehicleAssetId assetId);
    List<VehicleAsset> listAllOperableAssets();
}

public interface RetireVehicleAssetUseCase {
    void retireAssetPermanently(VehicleAssetId assetId, RetirementReason reason);
}

// ❌ Incorrecto — sufijo Service oculta que es un puerto de entrada
public interface VehicleAssetService { ... }   // ¿dominio o infraestructura?
public interface VehicleService { ... }         // demasiado genérico
```

**Implementación de UseCase:**

```java
// ✅ La implementación lleva el sufijo de la interfaz + Impl
@Service
public class RegisterVehicleAssetUseCaseImpl implements RegisterVehicleAssetUseCase {

    private final VehicleAssetRepositoryPort vehicleAssetRepository;
    private final EventPublisherPort eventPublisher;

    // Constructor injection — Lombok @RequiredArgsConstructor o constructor explícito
    public RegisterVehicleAssetUseCaseImpl(
            VehicleAssetRepositoryPort vehicleAssetRepository,
            EventPublisherPort eventPublisher) {
        this.vehicleAssetRepository = vehicleAssetRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public VehicleAsset registerAsset(RegisterVehicleAssetCommand command) {
        // ...
    }
}
```

### 3.2 Puertos de Salida

**Sufijo:** `Port`  
**Convención:** `<Concepto><Función>Port`

```java
// ✅ Correcto
public interface VehicleAssetRepositoryPort {
    VehicleAsset save(VehicleAsset asset);
    Optional<VehicleAsset> findById(VehicleAssetId assetId);
    List<VehicleAsset> findAllOperable();
}

public interface EventPublisherPort {
    void publishDomainEvent(DomainEvent event);
}

public interface NotificationDispatcherPort {
    void dispatchDriverNotification(DriverNotification notification);
}

// ❌ Incorrecto
public interface VehicleRepository { ... }       // sin sufijo Port, parece Spring Data
public interface EventPublisher { ... }          // sin sufijo Port, ambiguo
public interface IVehicleRepository { ... }      // prefijo I al estilo C#, no Java
```

### 3.3 Adaptadores de Entrada REST

**Sufijo:** `RestAdapter` o `Controller`  
Los métodos del controlador deben nombrarse según la **acción HTTP + recurso**.

```java
// ✅ Correcto
@RestController
@RequestMapping("/api/v1/vehicle-assets")
public class VehicleAssetRestAdapter {

    private final RegisterVehicleAssetUseCase registerVehicleAssetUseCase;
    private final GetVehicleAssetUseCase getVehicleAssetUseCase;

    @PostMapping
    public ResponseEntity<VehicleAssetResponse> registerVehicleAsset(
            @RequestBody @Valid RegisterVehicleAssetRequest request) { ... }

    @GetMapping("/{assetId}")
    public ResponseEntity<VehicleAssetResponse> getVehicleAssetById(
            @PathVariable UUID assetId) { ... }
}

// ❌ Incorrecto
@RestController
public class VehicleController { ... }      // genérico sin contexto de bounded context
public class VehicleAssetAPI { ... }        // sufijo incorrecto
public class VehicleAssetEndpoint { ... }   // sufijo no estándar en el proyecto
```

### 3.4 Adaptadores de Salida JPA

**Sufijo:** `JpaRepositoryAdapter` o `JpaAdapter`

```java
// ✅ Correcto
@Repository
public class VehicleAssetJpaRepositoryAdapter implements VehicleAssetRepositoryPort {

    private final VehicleAssetSpringDataJpaRepository springDataRepository;
    private final VehicleAssetJpaEntityMapper mapper;

    // Implementa el puerto usando Spring Data internamente
    @Override
    public VehicleAsset save(VehicleAsset asset) {
        VehicleAssetJpaEntity entity = mapper.toJpaEntity(asset);
        VehicleAssetJpaEntity savedEntity = springDataRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }
}

// Repositorio Spring Data — sufijo SpringDataJpaRepository para diferenciarlo del puerto
public interface VehicleAssetSpringDataJpaRepository
        extends JpaRepository<VehicleAssetJpaEntity, UUID> { ... }

// ❌ Incorrecto
public class VehicleAssetRepositoryImpl implements VehicleAssetRepositoryPort { ... }
// "Impl" no expresa la tecnología subyacente
```

### 3.5 Adaptadores de Salida Kafka

**Sufijo:** `KafkaPublisherAdapter`

```java
// ✅ Correcto
@Component
public class VehicleEventKafkaPublisherAdapter implements EventPublisherPort {

    private static final String VEHICLE_EVENTS_TOPIC = "urbanmove.vehicle.events.v1";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishDomainEvent(DomainEvent event) {
        kafkaTemplate.send(VEHICLE_EVENTS_TOPIC, event.aggregateId().toString(), event);
    }
}

// ❌ Incorrecto
public class EventPublisherImpl implements EventPublisherPort { ... }
public class KafkaService { ... }
public class VehicleEventProducer { ... }   // sin sufijo Adapter
```

### 3.6 Entidades JPA

**Sufijo:** `JpaEntity` — **siempre**, para distinguirlas del modelo de dominio.

```java
// ✅ Correcto — entidad JPA con sufijo diferenciador
@Entity
@Table(name = "vehicle_assets")
public class VehicleAssetJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "license_plate", nullable = false, unique = true)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VehicleStatusJpa status;   // también el enum JPA lleva contexto
}

// ❌ Incorrecto — nombre de entidad JPA idéntico al modelo de dominio
@Entity
public class VehicleAsset { ... }   // ¡colisión con el record de dominio!
@Entity
public class Vehicle { ... }        // genérico, sin sufijo JpaEntity
```

### 3.7 Comandos

**Sufijo:** `Command`  
Son Records inmutables que representan la **intención de modificar el estado** del sistema.

```java
// ✅ Correcto
public record RegisterVehicleAssetCommand(
    LicensePlate licensePlate,
    VehicleModel vehicleModel,
    PurchaseDate purchaseDate,
    FleetId targetFleetId
) {}

public record RetireVehicleAssetCommand(
    VehicleAssetId assetId,
    RetirementReason reason,
    LocalDate retirementDate
) {}

// ❌ Incorrecto
public record VehicleAssetDto(...) {}      // Dto es un sufijo genérico, no expresa intención
public class RegisterRequest { ... }       // sufijo Request es para DTOs REST, no comandos
public record AssetData(...) {}            // sin ningún significado intencional
```

### 3.8 Eventos de Dominio

**Sufijo:** `Event`  
Son Records inmutables en **tiempo pasado** (algo que ya sucedió).

```java
// ✅ Correcto — nombre en tiempo pasado, sufijo Event
public record VehiclePurchasedEvent(
    VehicleAssetId assetId,
    LicensePlate licensePlate,
    FleetId fleetId,
    Instant occurredAt
) implements DomainEvent {}

public record VehicleRetiredEvent(
    VehicleAssetId assetId,
    RetirementReason reason,
    Instant occurredAt
) implements DomainEvent {}

// ❌ Incorrecto
public record VehiclePurchase(...) {}       // tiempo presente, no expresa que es un evento
public record VehicleEvent(...) {}          // genérico, sin discriminar qué evento
public class PurchasedEvent { ... }         // clase mutable cuando debería ser Record
```

### 3.9 DTOs de Petición y Respuesta REST

**Sufijo:** `Request` para entrada, `Response` para salida.  
Son Records inmutables (o clases con Jackson anotadas si se requiere polimorfismo).

```java
// ✅ Correcto — DTOs como Records con validación Jakarta
public record RegisterVehicleAssetRequest(
    @NotBlank String licensePlate,
    @NotNull String vehicleModelCode,
    @NotNull @PastOrPresent LocalDate purchaseDate,
    @NotNull UUID fleetId
) {}

public record VehicleAssetResponse(
    UUID id,
    String licensePlate,
    String vehicleModel,
    String status,
    LocalDate purchaseDate
) {}

// ❌ Incorrecto
public class VehicleAssetDTO { ... }         // sufijo DTO genérico, mutable
public record VehicleData(...) {}            // sin distinguir entrada/salida
public record AssetPayload(...) {}           // "payload" no expresa la capa
```

---

## 4. Prohibición de Nombres Genéricos en el Monorepo

En un monorepo con múltiples bounded contexts, los nombres deben ser **unívocos globalmente**.

| ❌ Nombre genérico | Problema | ✅ Nombre específico por contexto |
|---|---|---|
| `Vehicle` | ¿inventario, disponibilidad o viajes? | `VehicleAsset` (inventario), `AvailableResource` (disponibilidad) |
| `VehicleService` | ¿qué operaciones? ¿qué bounded context? | `RegisterVehicleAssetUseCase`, `GetAvailableResourceUseCase` |
| `VehicleRepository` | sin sufijo Port, sin contexto | `VehicleAssetRepositoryPort`, `AvailableResourceRepositoryPort` |
| `VehicleEntity` | ¿JPA? ¿dominio? ¿qué microservicio? | `VehicleAssetJpaEntity` |
| `VehicleMapper` | ¿de qué a qué? | `VehicleAssetJpaEntityMapper`, `VehicleAssetResponseMapper` |
| `EventService` | ¿publica? ¿consume? ¿qué eventos? | `VehicleEventKafkaPublisherAdapter` |
| `UserService` | en un sistema multimicroservicio | `DriverProfileUseCase`, `PassengerRegistrationUseCase` |

---

## 5. Tabla de Sufijos por Artefacto (Referencia Rápida)

| Artefacto | Sufijo | Ejemplo |
|---|---|---|
| Puerto de entrada (interfaz) | `UseCase` | `RegisterVehicleAssetUseCase` |
| Implementación de caso de uso | `UseCaseImpl` | `RegisterVehicleAssetUseCaseImpl` |
| Puerto de salida (interfaz) | `Port` | `VehicleAssetRepositoryPort` |
| Adaptador REST | `RestAdapter` | `VehicleAssetRestAdapter` |
| Adaptador JPA | `JpaRepositoryAdapter` | `VehicleAssetJpaRepositoryAdapter` |
| Repositorio Spring Data | `SpringDataJpaRepository` | `VehicleAssetSpringDataJpaRepository` |
| Adaptador Kafka (productor) | `KafkaPublisherAdapter` | `VehicleEventKafkaPublisherAdapter` |
| Entidad JPA | `JpaEntity` | `VehicleAssetJpaEntity` |
| Modelo de dominio | sin sufijo | `VehicleAsset`, `TripFare` |
| Comando | `Command` | `RegisterVehicleAssetCommand` |
| Evento de dominio | `Event` | `VehiclePurchasedEvent` |
| DTO petición REST | `Request` | `RegisterVehicleAssetRequest` |
| DTO respuesta REST | `Response` | `VehicleAssetResponse` |
| Mapper de capas | `Mapper` + contexto | `VehicleAssetJpaEntityMapper` |
| Value Object | nombre del concepto | `LicensePlate`, `FleetId`, `TripFare` |

---

## 6. Checklist de Revisión de Nomenclatura

Antes de hacer commit, verificar:

- [ ] ¿Cada clase/record tiene un nombre que revela su concepto sin ambigüedad en el monorepo?
- [ ] ¿Los métodos usan verbos con intención clara (no `process`, `handle`, `manage`)?
- [ ] ¿Las variables no usan abreviaciones (`repo`, `svc`, `dto`, `obj`, `temp`)?
- [ ] ¿Los booleanos usan `is`, `has` o `can` como prefijo?
- [ ] ¿Las constantes están en `UPPER_SNAKE_CASE` con nombre descriptivo?
- [ ] ¿Los paquetes incluyen el nombre del microservicio/bounded context?
- [ ] ¿Los puertos de entrada tienen sufijo `UseCase` y los de salida `Port`?
- [ ] ¿Las entidades JPA tienen el sufijo `JpaEntity`?
- [ ] ¿Los comandos tienen sufijo `Command` y los eventos `Event`?
- [ ] ¿Los DTOs REST tienen sufijo `Request` o `Response`?
