---
name: java-code-quality-readability
description: >
  Calidad y legibilidad del código Java para el proyecto UrbanMove.
  Define reglas prácticas sobre tamaño de métodos, parámetros, comentarios,
  magic numbers, niveles de abstracción, Optional, guard clauses, logging
  estructurado y pattern matching. Actívala siempre que se genere o revise
  código Java en cualquier microservicio de UrbanMove.
---

# Java Code Quality & Readability — UrbanMove

> El código se lee muchas más veces de las que se escribe.
> Estas reglas hacen que el código sea **autoexplicativo, mantenible y seguro**.

---

## 1. Límite de Tamaño de Métodos — Máximo 20 líneas

Un método que supera 20 líneas está haciendo más de una cosa. La solución es **extraer responsabilidades** en métodos privados con nombres descriptivos.

```java
// ❌ Incorrecto — método de 35+ líneas con múltiples niveles de abstracción mezclados
public VehicleAsset registerAsset(RegisterVehicleAssetCommand command) {
    if (command == null) {
        throw new IllegalArgumentException("Command cannot be null");
    }
    if (command.licensePlate() == null || command.licensePlate().isBlank()) {
        throw new IllegalArgumentException("License plate is required");
    }
    Optional<VehicleAssetJpaEntity> existing =
        springDataRepo.findByLicensePlate(command.licensePlate());
    if (existing.isPresent()) {
        throw new VehicleAssetAlreadyExistsException(command.licensePlate());
    }
    VehicleAsset newAsset = new VehicleAsset(
        VehicleAssetId.generate(),
        new LicensePlate(command.licensePlate()),
        VehicleModel.fromCode(command.vehicleModelCode()),
        command.purchaseDate(),
        VehicleStatus.OPERABLE
    );
    VehicleAssetJpaEntity entity = mapper.toJpaEntity(newAsset);
    VehicleAssetJpaEntity saved = springDataRepo.save(entity);
    VehicleAsset savedAsset = mapper.toDomain(saved);
    VehiclePurchasedEvent event = new VehiclePurchasedEvent(
        savedAsset.id(), savedAsset.licensePlate(), command.fleetId(), Instant.now()
    );
    kafkaTemplate.send("urbanmove.vehicle.events.v1", event);
    log.info("Vehicle asset registered: {}", savedAsset.id());
    return savedAsset;
}

// ✅ Correcto — método principal de ~10 líneas, responsabilidades delegadas
public VehicleAsset registerAsset(RegisterVehicleAssetCommand command) {
    validateNoDuplicateLicensePlate(command.licensePlate());
    VehicleAsset newAsset = buildNewVehicleAsset(command);
    VehicleAsset savedAsset = vehicleAssetRepository.save(newAsset);
    publishVehiclePurchasedEvent(savedAsset, command.fleetId());
    log.info("Vehicle asset registered with id={}", savedAsset.id());
    return savedAsset;
}

private void validateNoDuplicateLicensePlate(LicensePlate licensePlate) {
    vehicleAssetRepository.findByLicensePlate(licensePlate)
        .ifPresent(existing -> {
            throw new VehicleAssetAlreadyExistsException(licensePlate);
        });
}

private VehicleAsset buildNewVehicleAsset(RegisterVehicleAssetCommand command) {
    return new VehicleAsset(
        VehicleAssetId.generate(),
        command.licensePlate(),
        VehicleModel.fromCode(command.vehicleModelCode()),
        command.purchaseDate(),
        VehicleStatus.OPERABLE
    );
}

private void publishVehiclePurchasedEvent(VehicleAsset asset, FleetId fleetId) {
    VehiclePurchasedEvent event = new VehiclePurchasedEvent(
        asset.id(), asset.licensePlate(), fleetId, Instant.now()
    );
    eventPublisher.publishDomainEvent(event);
}
```

---

## 2. Límite de Parámetros — Máximo 3 por Método

Más de 3 parámetros es una señal de que el método necesita un **objeto de parámetros** (Command o Record).

```java
// ❌ Incorrecto — 6 parámetros, fácil confundir el orden
public VehicleAsset createAsset(
        String licensePlate,
        String modelCode,
        LocalDate purchaseDate,
        UUID fleetId,
        String color,
        int seatCapacity) { ... }

// ✅ Correcto — objeto de parámetros como Record
public record RegisterVehicleAssetCommand(
    LicensePlate licensePlate,
    String vehicleModelCode,
    LocalDate purchaseDate,
    FleetId fleetId,
    VehicleColor color,
    SeatCapacity seatCapacity
) {}

public VehicleAsset registerAsset(RegisterVehicleAssetCommand command) { ... }
```

```java
// ❌ Incorrecto — método de búsqueda con muchos filtros como parámetros
public List<VehicleAsset> searchAssets(
        String status,
        UUID fleetId,
        String modelCode,
        LocalDate purchasedAfter,
        boolean includeRetired) { ... }

// ✅ Correcto — criterios de búsqueda encapsulados
public record VehicleAssetSearchCriteria(
    Optional<VehicleStatus> status,
    Optional<FleetId> fleetId,
    Optional<String> vehicleModelCode,
    Optional<LocalDate> purchasedAfter,
    boolean includeRetired
) {
    public static VehicleAssetSearchCriteria onlyOperable(FleetId fleetId) {
        return new VehicleAssetSearchCriteria(
            Optional.of(VehicleStatus.OPERABLE),
            Optional.of(fleetId),
            Optional.empty(),
            Optional.empty(),
            false
        );
    }
}

public List<VehicleAsset> searchAssets(VehicleAssetSearchCriteria criteria) { ... }
```

---

## 3. Política de Comentarios

### 3.1 Código autoexplicativo — cero comentarios de "qué"

El código bien nombrado no necesita comentarios que expliquen lo que hace.

```java
// ❌ Incorrecto — comentario redundante que repite el código
// Get the vehicle asset from the repository by id
Optional<VehicleAsset> asset = vehicleAssetRepository.findById(assetId);

// Check if the asset is operable
if (asset.isPresent() && asset.get().isOperable()) {
    // Calculate the fare for the trip
    TripFare fare = tripFareCalculator.calculateTripFare(route, farePolicy);
}

// ✅ Correcto — sin comentarios, el código habla por sí mismo
Optional<VehicleAsset> nearbyAsset = vehicleAssetRepository.findById(assetId);
nearbyAsset
    .filter(VehicleAsset::isOperable)
    .map(asset -> tripFareCalculator.calculateTripFare(route, farePolicy))
    .ifPresent(this::notifyDriverOfFareEstimate);
```

### 3.2 JavaDoc para API pública — qué garantiza, no cómo lo hace

```java
// ✅ Correcto — JavaDoc describe el contrato, no la implementación
/**
 * Registers a new vehicle asset in the fleet inventory.
 *
 * <p>The asset is created with {@link VehicleStatus#OPERABLE} status and a
 * {@link VehiclePurchasedEvent} is published upon successful registration.
 *
 * @param command the registration command with asset details; must not be null
 * @return the persisted vehicle asset with its generated {@link VehicleAssetId}
 * @throws VehicleAssetAlreadyExistsException if the license plate is already registered
 * @throws InvalidVehicleModelException if the model code does not correspond to a known model
 */
VehicleAsset registerAsset(RegisterVehicleAssetCommand command);
```

### 3.3 Comentarios de "por qué" — solo para decisiones técnicas no obvias

```java
// ✅ Correcto — explica una decisión técnica, no lo que hace el código
// Using pessimistic locking here because concurrent retirement requests for the same
// asset must be serialized — optimistic locking caused too many RetryExceptions
// under peak load (see ADR-012).
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<VehicleAssetJpaEntity> findByIdForUpdate(UUID id);
```

---

## 4. Prohibición de Magic Numbers y Magic Strings

Todo literal con significado de negocio debe ser una **constante con nombre descriptivo**.

```java
// ❌ Incorrecto — magic numbers y strings dispersos
if (tripDurationMinutes > 120) {
    fare = fare.multiply(new BigDecimal("1.15"));
}
kafkaTemplate.send("urbanmove.vehicle.events.v1", event);
if (retryCount >= 3) { ... }

// ✅ Correcto — constantes con nombres descriptivos
private static final int LONG_TRIP_THRESHOLD_MINUTES = 120;
private static final BigDecimal LONG_TRIP_SURCHARGE_MULTIPLIER = new BigDecimal("1.15");
private static final String VEHICLE_EVENTS_TOPIC = "urbanmove.vehicle.events.v1";
private static final int MAX_EVENT_PUBLISH_RETRIES = 3;

if (tripDurationMinutes > LONG_TRIP_THRESHOLD_MINUTES) {
    fare = fare.multiply(LONG_TRIP_SURCHARGE_MULTIPLIER);
}
kafkaTemplate.send(VEHICLE_EVENTS_TOPIC, event);
if (retryCount >= MAX_EVENT_PUBLISH_RETRIES) { ... }
```

```java
// ❌ Incorrecto — magic string en anotación de columna sin constante
@Column(name = "status", length = 50)

// ✅ Correcto — longitud documentada como constante
private static final int VEHICLE_STATUS_COLUMN_MAX_LENGTH = 50;

@Column(name = "status", length = VEHICLE_STATUS_COLUMN_MAX_LENGTH)
```

---

## 5. Separación de Niveles de Abstracción

Un método debe operar en **un único nivel de abstracción**. No mezclar lógica de negocio con detalles técnicos (SQL, Kafka, logs).

```java
// ❌ Incorrecto — mezcla lógica de negocio con infraestructura en el mismo método
public void retireAssetPermanently(VehicleAssetId assetId, RetirementReason reason) {
    // Nivel de negocio: ¿puede retirarse?
    VehicleAsset asset = vehicleAssetRepository.findById(assetId)
        .orElseThrow(() -> new VehicleAssetNotFoundException(assetId));

    // Nivel de infraestructura: validación de estado en base de datos
    if (springDataRepo.existsActiveRidesForAsset(assetId.value())) {
        throw new AssetHasActiveRidesException(assetId);
    }

    // Nivel de negocio: transición de estado
    VehicleAsset retiredAsset = asset.withStatus(VehicleStatus.PERMANENTLY_RETIRED);

    // Nivel de infraestructura: persistencia directa con JPA
    VehicleAssetJpaEntity entity = new VehicleAssetJpaEntity();
    entity.setId(retiredAsset.id().value());
    entity.setStatus(retiredAsset.status().name());
    springDataRepo.save(entity);

    // Nivel de infraestructura: publicación de evento
    kafkaTemplate.send("urbanmove.vehicle.events.v1", new VehicleRetiredEvent(...));
}

// ✅ Correcto — el método de aplicación habla solo de negocio
public void retireAssetPermanently(VehicleAssetId assetId, RetirementReason reason) {
    VehicleAsset asset = findOperableAssetOrThrow(assetId);
    ensureAssetHasNoActiveRides(assetId);
    VehicleAsset retiredAsset = asset.retire(reason);                // lógica en dominio
    vehicleAssetRepository.save(retiredAsset);                       // puerto de salida
    publishVehicleRetiredEvent(retiredAsset, reason);                // puerto de salida
}

// Los detalles técnicos se encapsulan en métodos privados descriptivos
private VehicleAsset findOperableAssetOrThrow(VehicleAssetId assetId) {
    return vehicleAssetRepository.findById(assetId)
        .filter(VehicleAsset::isOperable)
        .orElseThrow(() -> new VehicleAssetNotFoundException(assetId));
}

private void ensureAssetHasNoActiveRides(VehicleAssetId assetId) {
    if (rideSessionRepository.existsActiveRideForAsset(assetId)) {
        throw new AssetHasActiveRidesException(assetId);
    }
}
```

---

## 6. Uso Correcto de `Optional`

### Reglas de Optional

| Regla | ❌ Violación | ✅ Correcto |
|---|---|---|
| Nunca pasar Optional como parámetro | `findAsset(Optional<UUID> id)` | `findAsset(UUID id)` |
| Optional nunca puede ser null | `return null;` en método que retorna Optional | `return Optional.empty();` |
| Usar `orElseThrow` con mensaje | `.get()` sin verificar | `.orElseThrow(() -> new VehicleAssetNotFoundException(id))` |
| No usar para colecciones | `Optional<List<T>>` | `List<T>` vacío si no hay elementos |
| Solo para retorno, no campos | `private Optional<String> name;` | `private String name;` (nullable con @Nullable) |

```java
// ❌ Incorrecto — múltiples violaciones
public Optional<VehicleAsset> findAsset(Optional<VehicleAssetId> assetId) {
    if (!assetId.isPresent()) {
        return null;                    // Optional nunca puede ser null
    }
    Optional<VehicleAsset> result = repository.findById(assetId.get());
    if (result.isPresent()) {
        return Optional.of(result.get());
    }
    return Optional.empty();
}

// ✅ Correcto
public Optional<VehicleAsset> findAsset(VehicleAssetId assetId) {
    return vehicleAssetRepository.findById(assetId);
}

// ✅ Correcto — encadenamiento fluido de Optional
public TripFare calculateFareForNearbyAsset(GeoCoordinate pickupLocation, TripRoute route) {
    return vehicleAssetRepository.findNearestOperableAsset(pickupLocation)
        .map(asset -> tripFareCalculator.calculateTripFare(route, asset.farePolicy()))
        .orElseThrow(() -> new NoAvailableAssetNearLocationException(pickupLocation));
}
```

---

## 7. Guard Clauses — Retorno/Excepción Temprana

Evitar el "arrow code" (código anidado en pirámide). Fallar **rápido y claro**.

```java
// ❌ Incorrecto — múltiples niveles de anidamiento ("arrow code")
public VehicleAsset processAssetRegistration(RegisterVehicleAssetCommand command) {
    if (command != null) {
        if (command.licensePlate() != null) {
            if (!vehicleAssetRepository.existsByLicensePlate(command.licensePlate())) {
                if (command.fleetId() != null) {
                    VehicleAsset asset = buildNewVehicleAsset(command);
                    return vehicleAssetRepository.save(asset);
                } else {
                    throw new IllegalArgumentException("Fleet ID is required");
                }
            } else {
                throw new VehicleAssetAlreadyExistsException(command.licensePlate());
            }
        } else {
            throw new IllegalArgumentException("License plate is required");
        }
    } else {
        throw new IllegalArgumentException("Command cannot be null");
    }
}

// ✅ Correcto — guard clauses, el "happy path" es el flujo principal
public VehicleAsset registerAsset(RegisterVehicleAssetCommand command) {
    Objects.requireNonNull(command, "RegisterVehicleAssetCommand must not be null");
    Objects.requireNonNull(command.licensePlate(), "License plate must not be null");
    Objects.requireNonNull(command.fleetId(), "Fleet ID must not be null");

    if (vehicleAssetRepository.existsByLicensePlate(command.licensePlate())) {
        throw new VehicleAssetAlreadyExistsException(command.licensePlate());
    }

    VehicleAsset newAsset = buildNewVehicleAsset(command);
    return vehicleAssetRepository.save(newAsset);
}
```

---

## 8. Pattern Matching y Sealed Interfaces — Evitar `instanceof` Explícito

Con sealed interfaces y Java 21+, usar `switch` exhaustivo en lugar de cadenas de `instanceof`.

```java
// Modelo de dominio con sealed interface
public sealed interface VehicleStatusTransition
    permits ActivationTransition, MaintenanceTransition, RetirementTransition {}

public record ActivationTransition(VehicleAssetId assetId) implements VehicleStatusTransition {}
public record MaintenanceTransition(VehicleAssetId assetId, MaintenanceType type) implements VehicleStatusTransition {}
public record RetirementTransition(VehicleAssetId assetId, RetirementReason reason) implements VehicleStatusTransition {}

// ❌ Incorrecto — cadena de instanceof, no exhaustiva y verbose
public void applyTransition(VehicleStatusTransition transition) {
    if (transition instanceof ActivationTransition activationTransition) {
        activateAsset(activationTransition.assetId());
    } else if (transition instanceof MaintenanceTransition maintenanceTransition) {
        scheduleMaintenanceForAsset(maintenanceTransition.assetId(), maintenanceTransition.type());
    } else if (transition instanceof RetirementTransition retirementTransition) {
        retireAssetPermanently(retirementTransition.assetId(), retirementTransition.reason());
    }
    // Si se añade un nuevo caso a la sealed interface, el compilador NO avisa
}

// ✅ Correcto — switch exhaustivo con pattern matching; el compilador exige todos los casos
public void applyTransition(VehicleStatusTransition transition) {
    switch (transition) {
        case ActivationTransition(var assetId) ->
            activateAsset(assetId);
        case MaintenanceTransition(var assetId, var maintenanceType) ->
            scheduleMaintenanceForAsset(assetId, maintenanceType);
        case RetirementTransition(var assetId, var reason) ->
            retireAssetPermanently(assetId, reason);
    }
    // El compilador obliga a cubrir todos los casos de la sealed interface
}
```

---

## 9. Builder Pattern — Para Objetos con Más de 4 Campos Opcionales

Usar Builder (Lombok `@Builder`) cuando hay muchos campos opcionales en clases que **no pueden ser Records** (e.g. entidades JPA con ciclo de vida mutable).

```java
// ❌ Incorrecto — constructor con 7 parámetros, algunos opcionales
VehicleFleetReport report = new VehicleFleetReport(
    fleetId, reportDate, totalAssets, operableCount, null, null, null
);

// ✅ Correcto — Builder con Lombok para clase con campos opcionales
@Builder
public class VehicleFleetReport {
    private final FleetId fleetId;
    private final LocalDate reportDate;
    private final int totalAssetCount;
    private final int operableAssetCount;
    @Builder.Default
    private final int maintenanceAssetCount = 0;
    @Builder.Default
    private final int retiredAssetCount = 0;
    private final String generatedByUserId;  // opcional
}

// Uso legible y seguro ante nuevos campos
VehicleFleetReport report = VehicleFleetReport.builder()
    .fleetId(fleetId)
    .reportDate(LocalDate.now())
    .totalAssetCount(assets.size())
    .operableAssetCount(operableAssets.size())
    .build();
```

> **Nota:** Los Records de Java se construyen directamente — no necesitan Builder porque todos sus campos son requeridos en el constructor canónico. Usar objetos de parámetros intermedios si se necesita construcción parcial.

---

## 10. Logging Estructurado con SLF4J

### Reglas de Logging

| Nivel | Cuándo usarlo |
|---|---|
| `DEBUG` | Trazas de flujo interno, valores intermedios en lógica de negocio |
| `INFO` | Eventos de negocio relevantes (asset registrado, evento publicado, viaje iniciado) |
| `WARN` | Condiciones recuperables (reintentos de conexión, asset no encontrado en caché) |
| `ERROR` | Errores irrecuperables que requieren intervención (pérdida de conectividad con Kafka) |

```java
// ❌ Incorrecto — concatenación de strings (costosa si DEBUG está desactivado)
log.debug("Processing vehicle asset with id: " + assetId + " and status: " + status);
log.info("Asset " + assetId.value() + " registered successfully");
log.error("Error: " + exception.getMessage());

// ❌ Incorrecto — llamar toString() o métodos adicionales al formar el mensaje
log.debug("Asset: " + asset.toString());

// ✅ Correcto — parámetros lazy con {} (SLF4J evalúa solo si el nivel está activo)
log.debug("Processing vehicle asset id={} with status={}", assetId, status);
log.info("Vehicle asset registered id={} licensePlate={}", asset.id(), asset.licensePlate());
log.warn("Vehicle asset id={} not found in availability cache, falling back to database", assetId);
log.error("Failed to publish VehiclePurchasedEvent for assetId={}", assetId, exception);
// Nota: el último parámetro puede ser un Throwable — SLF4J lo imprime con stack trace
```

```java
// ✅ Correcto — contexto de correlación con MDC para trazabilidad distribuida
public VehicleAsset registerAsset(RegisterVehicleAssetCommand command) {
    MDC.put("correlationId", UUID.randomUUID().toString());
    MDC.put("licensePlate", command.licensePlate().value());
    try {
        log.info("Starting vehicle asset registration");
        VehicleAsset savedAsset = persistAndPublish(command);
        log.info("Vehicle asset registration completed assetId={}", savedAsset.id());
        return savedAsset;
    } finally {
        MDC.clear();  // siempre limpiar en finally para evitar leaks en thread pools
    }
}
```

---

## 11. Fluent API y Stream Pipelines

Usar method chaining **solo donde mejora la legibilidad**: principalmente en Stream pipelines.  
**Nunca** encadenar métodos de lógica de negocio que deben ejecutarse con efectos laterales.

```java
// ✅ Correcto — pipeline de Stream legible y declarativo
public FleetSummary calculateFleetSummary(FleetId fleetId) {
    List<VehicleAsset> assets = vehicleAssetRepository.findAllByFleet(fleetId);

    Map<VehicleStatus, Long> countByStatus = assets.stream()
        .collect(Collectors.groupingBy(VehicleAsset::status, Collectors.counting()));

    List<VehicleAsset> operableAssets = assets.stream()
        .filter(VehicleAsset::isOperable)
        .toList();  // Java 16+ — produce lista inmodificable

    return new FleetSummary(fleetId, assets.size(), countByStatus, operableAssets.size());
}

// ❌ Incorrecto — method chaining en lógica de negocio con efectos laterales
// El orden de ejecución no es obvio y es difícil de depurar
return assetBuilder
    .validate()
    .enrichWithFleetData()
    .persist()
    .publishEvent()
    .toResponse();
```

---

## 12. Checklist de Calidad de Código

Antes de hacer commit, verificar:

- [ ] ¿Ningún método supera las 20 líneas?
- [ ] ¿Ningún método tiene más de 3 parámetros (o se usa un Command/Record)?
- [ ] ¿No hay comentarios de "qué" hace el código (solo JavaDoc de API pública y "por qué")?
- [ ] ¿Todos los literals de negocio son constantes con nombre descriptivo (`UPPER_SNAKE_CASE`)?
- [ ] ¿Cada método opera en un único nivel de abstracción?
- [ ] ¿`Optional` nunca se pasa como parámetro y nunca devuelve `null`?
- [ ] ¿Se usan guard clauses en lugar de anidamiento profundo?
- [ ] ¿Se usa `switch` con pattern matching en vez de cadenas de `instanceof` sobre sealed types?
- [ ] ¿El logging usa parámetros `{}` y nunca concatenación de strings?
- [ ] ¿Los niveles de log (`DEBUG`/`INFO`/`WARN`/`ERROR`) son semánticamente correctos?
- [ ] ¿Los stream pipelines son declarativos y sin efectos laterales ocultos?
