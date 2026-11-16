# ADR 0012 — `core-fleet` como monolito modular temporal con split planeado en microservicios

| Campo       | Valor                          |
|-------------|--------------------------------|
| **Estado**  | Aceptado                       |
| **Fecha**   | 2026-11-16                     |
| **Autores** | Equipo UrbanMove               |
| **Fase**    | Fase 0 — MVP                   |

---

## Contexto

Durante la fase MVP (Fase 0) se identificaron tres dominios de negocio claramente diferenciados dentro de la gestión de flota:

| Dominio          | Responsabilidad                                                 |
|------------------|-----------------------------------------------------------------|
| **Availability** | Estado y disponibilidad de vehículos (`Vehicle`)               |
| **Reservations** | Creación y gestión de reservas (`Reservation`)                 |
| **Rides**        | Inicio, seguimiento y finalización de viajes (`Ride`)          |

La arquitectura hexagonal aplicada en `core-fleet` ya los separa internamente con sus propios modelos de dominio, casos de uso, puertos y adaptadores. Sin embargo, se tomó la decisión deliberada de mantenerlos dentro de **un único servicio Spring Boot** durante esta fase.

---

## Decisión

**`core-fleet` se implementa como un monolito modular** que agrupa los tres dominios (`availability`, `reservations`, `rides`) en un solo proceso JVM durante la Fase 0.

La separación en microservicios independientes se realizará en una fase posterior cuando exista una razón de negocio o de equipo que lo justifique.

---

## Justificación

### Por qué un monolito modular ahora

1. **Validar el flujo E2E primero**: antes de multiplicar la complejidad operativa, necesitamos confirmar que el flujo completo `reservar → iniciar viaje → finalizar → evento SQS → Lambda → S3` funciona de extremo a extremo.

2. **Coste de memoria en Docker Compose**: arrancar tres JVMs de 512 MB+ en desarrollo local consume más de 1,5 GB adicionales sin aportación inmediata de valor en Fase 0.

3. **Complejidad de red evitada**: con tres servicios independientes aparecerían inmediatamente problemas de service discovery, circuit breakers, contratos de API entre servicios y orden de arranque — problemas irrelevantes en esta fase.

4. **La arquitectura hexagonal hace que el split sea trivial**: cada dominio ya vive completamente aislado:
   - Sus propias entidades de dominio (`Vehicle`, `Reservation`, `Ride`)
   - Sus propios casos de uso (`GetVehiclesUseCase`, `ReserveVehicleUseCase`, `ManageRidesUseCase`)
   - Sus propios puertos de entrada y salida
   - Sus propios adaptadores JPA y SQS
   - Su propia base de datos (`availability_db`, `reservations_db`, `rides_db` — ya creadas en `init-databases.sql`)

   Cuando llegue el momento, el split es **copiar y pegar**, no reescribir.

---

## Estructura actual vs. estructura futura

### Fase 0 (actual) — Monolito modular

```
services/core-fleet/
└── src/main/java/com/urbanmove/fleet/
    ├── domain/
    │   ├── model/         → Vehicle, Reservation, Ride (tres dominios conviven)
    │   ├── event/         → ReservationCreatedEvent, TripRecordedEvent
    │   └── port/
    │       ├── in/        → GetVehiclesUseCase, ReserveVehicleUseCase, ManageRidesUseCase
    │       └── out/       → VehicleRepositoryPort, ReservationRepositoryPort, RideRepositoryPort
    ├── application/
    │   └── service/       → FleetApplicationService (implementa los 3 casos de uso)
    └── infrastructure/
        ├── in/web/        → FleetController, AdminController
        └── out/
            ├── persistence/ → Adaptadores JPA para los 3 dominios
            └── messaging/   → LocalstackSqsEventPublisherAdapter
```

### Fase futura — Tres microservicios independientes

```
services/
├── availability-service/   → Vehicle + VehicleRepository → availability_db
├── reservations-service/   → Reservation + ReservationRepository → reservations_db
└── rides-service/          → Ride + RideRepository → rides_db
                              (publica TripRecordedEvent → SQS)
```

Comunicación entre servicios: **REST (OpenFeign)** para consultas síncronas, **eventos SQS/SNS** para notificaciones asíncronas.

---

## Cuándo hacer el split

Se recomienda dividir `core-fleet` cuando se cumpla **al menos una** de estas condiciones:

- [ ] Se incorpora un segundo desarrollador que tomará ownership de uno de los dominios
- [ ] Se añade autenticación con Keycloak y cada servicio necesita políticas de autorización distintas
- [ ] El volumen de reservas o viajes requiere escalar independientemente del servicio de disponibilidad
- [ ] Se detecta contención de base de datos entre los tres dominios bajo carga
- [ ] Se inicia la **Fase 1** del roadmap del proyecto

---

## Consecuencias

### Positivas
- Iteración más rápida en Fase 0 con menor overhead operativo
- Un único JAR para construir, testear y desplegar en CI
- Deuda técnica controlada y explícita (documentada aquí)
- El split futuro no requiere refactorización del dominio

### Negativas / Deuda técnica aceptada
- `FleetApplicationService` mezcla lógica de tres dominios en un mismo servicio de aplicación
- `FleetController` concentra endpoints de tres contextos en un solo controlador
- El acoplamiento entre dominios podría crecer si no se mantiene la disciplina hexagonal
- Las llamadas entre dominios son directas (método Java) en lugar de HTTP/eventos — esto no es válido en producción

### Mitigaciones
- Los dominios están separados por paquetes: **prohibido importar** entre `model/Vehicle*`, `model/Reservation*` y `model/Ride*` directamente
- Cada futuro microservicio tendrá su propio módulo Maven o Gradle para facilitar la extracción
- Se revisará este ADR al iniciar Fase 1

---

## Alternativas consideradas

| Alternativa | Motivo de descarte |
|---|---|
| Tres microservicios desde el inicio | Overhead de infraestructura excesivo para MVP; complejidad sin valor demostrado |
| Un único `FleetController` sin separación de dominio | Rechazado: no cumple principios hexagonales, dificulta el split futuro |
| Módulos Maven separados dentro del mismo repo | Válido para Fase 1; añade complejidad de build innecesaria en Fase 0 |

---

## Referencias

- [ADR 0008 — MVP con arquitectura hexagonal](./0008-mvp-ver-y-reservar-hexagonal.md)
- [ADR 0010 — Lambda de ingesta y data lake S3](./0010-lambda-ingesta-s3-data-lake.md)
- [ADR 0011 — Terraform + LocalStack para IaC](./0011-terraform-localstack-iac.md)
- `infra/local/init-databases.sql` — las tres bases de datos ya existen: `availability_db`, `reservations_db`, `rides_db`
- Martin Fowler — *MonolithFirst* pattern: https://martinfowler.com/bliki/MonolithFirst.html
