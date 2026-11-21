# Estado y Avances del MVP — 21 de noviembre de 2026 (Sesión Tarde)

> Documento de situación para registrar el trabajo realizado en la resolución de deuda técnica (P0.1) 
> y la implementación completa del Outbox Transaccional con Sobre Canónico (P0.2).
>
> **Fecha:** 2026-11-21  
> **Rama:** `fase0-skeleton`  
> **Último commit:** `851a103`  
> **Ámbito:** `core-fleet` (monolito modular: `availability`, `reservations`, `rides`, `outbox`)

---

## 1. Resumen Ejecutivo

En esta sesión se cerraron dos bloques críticos del backend:
1. **Liquidación completa de la deuda técnica de P0.1**:
   - Aislamiento transaccional estricto por reserva caducada.
   - Concurrencia de barrido segura con `FOR UPDATE SKIP LOCKED`.
   - Eventos de dominio `ReservationExpiredEvent` y `ReservationCancelledEvent`.
   - Unificación del umbral de batería mínima a `< 15%`.
   - Compatibilidad de Testcontainers con Docker Engine reciente en CI y local.
2. **Implementación integral de P0.2 (Transactional Outbox & Event Envelope)**:
   - Persistencia atómica de eventos en PostgreSQL vía Flyway `V3__transactional_outbox.sql`.
   - Publicaciones inmutables (`event_publications`) sin `UPDATE` destructivo, alineado 100% con **ADR 0007**.
   - Sobre de evento canónico según especificación en [`EVENTS.md`](../01-arquitectura/EVENTS.md).
   - Trazabilidad distribuida con `CorrelationIdFilter` (`X-Correlation-Id` + MDC).
   - Relay asíncrono (`OutboxEventRelay`) con reclamo concurrente `SKIP LOCKED`, I/O fuera de transacción, reintentos exponenciales y política de `DEAD_LETTER`.
   - HealthIndicator para Spring Boot Actuator (`/actuator/health`).
   - Suite de pruebas unitarias (`OutboxEventRelayTest`) validando envíos a SQS, fallos transitorios y dead lettering.

---

## 2. Detalle de Cambios Implementados

### 2.1. Resolución de Deuda Técnica P0.1 (`58122ae`)
- **`SingleReservationExpirationHandler`**: Separado con `@Transactional(propagation = Propagation.REQUIRES_NEW)` para que el fallo de una reserva caducada aislada no contamine el lote global con `rollback-only`.
- **`SpringDataReservationRepository`**: Implementada query nativa `findByStatusAndExpiresAtBeforeWithSkipLocked` con `FOR UPDATE SKIP LOCKED`.
- **Eventos de Dominio**:
  - `ReservationExpiredEvent` emitido en la expiración.
  - `ReservationCancelledEvent` emitido al cancelar la reserva.
  - Actualizado `EventPublisherPort` y `LocalstackSqsEventPublisherAdapter`.
- **Alineación de Batería**: Umbral unificado a `< 15%` tanto en `ReservationService` como en `RideService`.

### 2.2. Corrección del Pipeline de CI / Docker API (`1a31cff`)
- **Problema**: Docker Engine moderno en los runners de GitHub Actions (`ubuntu-latest`) rechazaba la versión legacy `1.32` de la API cliente.
- **Solución**:
  - Actualizado Testcontainers a `1.20.4` en `pom.xml`.
  - Configurado `api.version=1.44` en `docker-java.properties` y variable `DOCKER_API_VERSION: '1.44'` en `.github/workflows/ci.yml`.

### 2.3. Implementación P0.2: Transactional Outbox y Event Envelope (`851a103`)
- **Migración Flyway `V3__transactional_outbox.sql`**:
  - `outbox_events`: Tabla append-only con campos del sobre canónico (`event_type`, `event_version`, `schema_uri`, `occurred_at`, `recorded_at`, `source`, `producer`, `correlation_id`, `causation_id`, `destination`, `payload JSONB`).
  - `event_publications`: Historial inmutable de publicaciones (`attempt_number`, `status: SUCCESS | FAILED | DEAD_LETTER`, `error_message`, `published_at`).
  - `view_pending_outbox_events`: Vista para identificar eventos no completados ni descartados.
- **Modelos de Dominio**:
  - `EventEnvelope<T>` con estructura estándar canónica.
  - Payloads específicos: `VehicleReservedPayload`, `ReservationCancelledPayload`, `ReservationExpiredPayload`, `TripRecordedPayload`.
- **Infraestructura**:
  - `CorrelationIdFilter`: Inyecta o extrae `X-Correlation-Id` en solicitudes HTTP y MDC.
  - `TransactionalOutboxEventPublisherAdapter`: `@Primary` sobre `EventPublisherPort`, encola eventos atómicamente en PostgreSQL.
  - `OutboxEventRelay`: Tarea `@Scheduled` que reclama lotes con `SKIP LOCKED`, efectúa la llamada de red a SQS de forma segura fuera del lock y persiste el intento en `event_publications`.
  - `OutboxHealthIndicator`: Monitoriza eventos totales, pendientes y en dead letter vía Actuator.
- **Pruebas**:
  - `OutboxEventRelayTest`: Cobertura completa del relay (éxito a SQS, reintento tras error, transición a DEAD_LETTER al 5º intento).

---

## 3. Historial de Commits de la Sesión

```text
851a103 feat(core-fleet): implement P0.2 transactional outbox, immutable publications and event envelope
1a31cff fix(ci): upgrade Testcontainers to 1.20.4 and set Docker API version to 1.44
58122ae feat(core-fleet): resolve P0.1 debt on scheduler isolation, SKIP LOCKED, events and battery threshold
```

---

## 4. Estado Actual del Monorepo

| Componente | Estado | Verificación |
|---|---|---|
| **Concurrencia y Doble Reserva** | Cerrado | `FleetConcurrencyIntegrationTest` (Testcontainers) |
| **Expiración de Reservas** | Cerrado | `ReservationExpirationSchedulerTest` con `REQUIRES_NEW` y `SKIP LOCKED` |
| **Idempotencia de Viajes** | Cerrado | Restricciones `uq_ride_reservation` y `uq_active_ride_per_vehicle` |
| **Transactional Outbox** | Cerrado | Flyway V3, tablas append-only, `OutboxEventRelayTest` |
| **Sobre de Evento Canónico** | Cerrado | `EventEnvelope<T>` alineado con `EVENTS.md` |
| **CI / GitHub Actions** | En ejecución | Runner Ubuntu con Docker y Java 21 |

---

## 5. Siguientes Pasos Priorizados

Siguiendo el orden de la rúbrica y la arquitectura del sistema:

### Paso 1: P0.3 — Máquina de Estados Completa y Consistencia Documental
1. **Ampliar `VehicleStatus`**:
   - Pasar de 4 a los 9 estados reglamentarios: `AVAILABLE`, `RESERVED`, `IN_USE`, `LOW_BATTERY`, `WITHDRAWN`, `CHARGING`, `REPAIRING`, `READY_FOR_SERVICE`, `RETIRED`.
   - Crear migración Flyway `V4__vehicle_state_machine.sql` con constraint `CHECK (status IN (...))`.
   - Modelar métodos de transición ricos en el dominio con validación de invariantes (Tell-Don't-Ask).
2. **Alineación Documental**:
   - Actualizar `README.md`, `PLAN.md`, `ACTION-PLAN.md`, `MANUAL-PRUEBAS-MVP.md` y `p0-remediation-design.md` (marcar P0.1 y P0.2 como completados y aprobados).

### Paso 2: Flujo End-to-End SQS → Lambda → S3 (Data Lake RAW)
- Provisionar la Lambda `analytics-ingest` y el Event Source Mapping desde la cola SQS de LocalStack.
- Comprobar que los eventos enviados por `OutboxEventRelay` a SQS son procesados por la Lambda y depositados en `s3://urbanmove-data-lake/raw/`.

### Paso 3: Contratos de API (P0.5)
- Crear directorio `contracts/`:
  - Especificación OpenAPI 3.0 (`fleet-openapi.yaml`) de los endpoints actuales.
  - JSON Schemas canónicos en `contracts/events/` para cada evento publicado (`VehicleReserved.v1.json`, `TripRecorded.v1.json`, etc.).
  - AsyncAPI para los canales de mensajería SQS.

### Paso 4: Transición a Fase P1
- Keycloak / Spring Security (JWT y roles en `core-fleet`).
- Capas Data Lake: Trusted y Curated (Parquet y marts analíticos).
- Split de `core-fleet` en microservicios independientes (`availability`, `reservations`, `rides`).
