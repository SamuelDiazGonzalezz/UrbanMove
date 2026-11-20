# Design Document: P0 Critical Remediation & Fleet Concurrency Hardening

* **Status**: Approved
* **Author**: Doffensmirthz
* **Date**: 2026-11-20
* **Target Components**: `services/core-fleet`, Event SQS Adapter, Database Schema (Flyway), Documentation (`README.md`, `PLAN.md`, `ACTION-PLAN.md`, `MANUAL-PRUEBAS-MVP.md`, `EVENTS.md`, `ADR 0007`, `ADR 0008`)

---

## 1. The Problem

The project currently has technical and architectural discrepancies that jeopardize grading rubric compliance:
1. **Concurrency Vulnerability (Double Booking)**: `FleetApplicationService.reserve()` executes an unprotected read-modify-write cycle (`findById` -> `markAsReserved` -> `save`). Without pessimistic/optimistic concurrency controls and without a database partial unique index (`UNIQUE(vehicle_id) WHERE status='ACTIVE'`), concurrent requests can double-reserve a vehicle.
2. **Missing Reservation Expiration Mechanism**: Active reservations lack an `expires_at` timestamp and an automatic expiration job. A partial unique index on active reservations without expiration acts as a permanent vehicle lock.
3. **Idempotency Gaps in Rides (`startRide` & `finishRide`)**: `startRide(reservationId)` can be invoked multiple times creating duplicate `Ride` records for the same reservation and vehicle. `finishRide` lacks concurrency guarding. Furthermore, walk-in ride scenarios (where a user starts a ride on an `AVAILABLE` vehicle without a prior reservation) are not properly supported.
4. **At-Risk Event Reliability & Architectural Contradiction**: `LocalstackSqsEventPublisherAdapter` catches exceptions and only logs them; failing calls drop domain events. Furthermore, an outbox table with in-place `UPDATE status='PUBLISHED'` directly contradicts ADR 0007 and `EVENTS.md`.
5. **Contract Discrepancies in Event Envelopes**: Event classes use Java class names (e.g. `ReservationCreatedEvent`, `TripRecordedEvent`) and string versions (`"1.0"`), contradicting `EVENTS.md` which mandates `eventVersion: 1`, `eventType: "VehicleReserved"`, `schema`, `source`, `recordedAt`, and correlation/causation tracking.
6. **State Machine Incompleteness**: `VehicleStatus` is missing required states from the assignment (`LOW_BATTERY`, `WITHDRAWN`, `CHARGING`, `REPAIRING`, `READY_FOR_SERVICE`, `RETIRED`).
7. **Documentation Discrepancies**: The root documentation (`README.md`, `PLAN.md`, `ACTION-PLAN.md`) does not accurately describe the operational status of `services/core-fleet`.

---

## 2. Overview & Goals

This design addresses P0 remediation items to achieve rubric compliance, strict data consistency under concurrent load, resilient outbox event propagation, and complete state machine lifecycles.

### Goals
* Enforce atomic single-reservation guarantees under concurrent load via database partial unique index, pessimistic locking during the monolith phase, and an automatic reservation expiration job.
* Enforce ride idempotency preventing duplicate active rides per reservation and per vehicle.
* Harmonize the Transactional Outbox pattern with ADR 0007 / `EVENTS.md`: implement an append-only publication tracking model (`outbox_event` + `event_publication`), exponential backoff, dead-letter tracking, and standard event envelopes.
* Complete the domain state machines for `Vehicle` (9 states) and `Reservation` (`ACTIVE`, `CANCELLED`, `EXPIRED`, `COMPLETED`), with an explicit transition matrix.
* Provide RFC 7807 `ProblemDetail` mapping for all concurrency conflicts (`409 Conflict`) and validation errors (`400 Bad Request`).
* Provide automated multithreaded and Testcontainers integration tests proving that competing reservation requests result in exactly one `201 Created` and competing requests receive `409 Conflict`.
* Reconcile documentation across the repository.

### Non-Goals
* Implementing Keycloak authentication and OAuth2/JWT security filters (assigned to P1).
* Decomposing `core-fleet` into 3 independently deployable microservices (assigned to P1).
* Constructing the full Medallion Parquet pipeline in analytics (`trusted/` and `curated/` data marts) (assigned to P1).
* Provisioning real AWS Academy resources via Terraform (assigned to P2).

---

## 3. Technical Plan

### 3.1. Concurrency Architecture & Reservation Lifecycle

#### 3.1.1. Single Slot State: `ACTIVE` with `expires_at`
To prevent the partial index from being bypassed, reservations will use **`ACTIVE`** as the single slot-occupying state upon creation. We avoid an intermediate `PENDING` state because no external payment/confirmation step exists in MVP.
* Every new reservation is created with `status = 'ACTIVE'`, `reserved_at = Instant.now(clock)`, and `expires_at = reserved_at.plus(15, ChronoUnit.MINUTES)`.
* **Database Partial Unique Index (Portable Invariant)**:
  ```sql
  CREATE UNIQUE INDEX uq_active_reservation_per_vehicle 
  ON reservations (vehicle_id) 
  WHERE status = 'ACTIVE';
  ```
  *Note on Microservice Portability*: While locking in `core-fleet` is an in-process monolithic optimization, this partial unique constraint on reservations is fully portable and remains valid when reservations becomes an independent atomic microservice.

#### 3.1.2. Concurrency Locking Strategy: Pessimistic Write Lock
To avoid detached entity mapping problems in `VehicleJpaEntity` and prevent deadlocks:
* We select **Pessimistic Locking (`PESSIMISTIC_WRITE`)** on `Vehicle` as the primary application-level lock for `core-fleet`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT v FROM VehicleJpaEntity v WHERE v.id = :id")
  Optional<VehicleJpaEntity> findByIdForUpdate(@Param("id") UUID id);
  ```
* Combined with the partial unique index, competing reservation requests execute sequentially in the database; the second transaction will either detect `vehicle.status() != AVAILABLE` and abort with a domain exception, or trigger a unique constraint violation on `reservations`. Both result in HTTP `409 Conflict`.

#### 3.1.3. Automatic Reservation Expiration Job
To ensure reservations do not lock vehicles indefinitely:
* Implement `ReservationExpirationScheduler` running at regular intervals (e.g. `@Scheduled(fixedDelayString = "${fleet.reservation.expiration-interval:10000}")`).
* Query reservations with `status = 'ACTIVE'` and `expires_at < NOW()`.
* Within a transaction for each batch:
  1. Transition reservation status to `EXPIRED`.
  2. Transition vehicle status from `RESERVED` back to `AVAILABLE`.
  3. Emit `ReservationExpired` domain event through the outbox.

---

### 3.2. Ride Idempotency & Lifecycle

#### 3.2.1. Ride Constraints & Uniqueness
To prevent duplicate rides:
1. **Unique Reservation on Ride**: A reservation cannot have more than one ride.
   ```sql
   ALTER TABLE rides ADD CONSTRAINT uq_ride_reservation UNIQUE (reservation_id);
   ```
2. **Partial Unique Index on Active Ride per Vehicle**: A vehicle cannot have more than one active trip.
   ```sql
   CREATE UNIQUE INDEX uq_active_ride_per_vehicle 
   ON rides (vehicle_id) 
   WHERE status = 'IN_PROGRESS';
   ```

#### 3.2.2. Walk-in Rides vs. Reserved Rides
* `Ride.reservation_id` is made **nullable** in both database schema and JPA entity to support walk-in rides directly from `AVAILABLE` vehicles.
* In `startRide`:
  * If `reservationId` is provided: validate reservation is `ACTIVE` and `expires_at >= NOW()`; validate vehicle is `RESERVED` for that user. Mark reservation `COMPLETED`.
  * If `reservationId` is null (walk-in): validate vehicle is `AVAILABLE`.
  * In both paths: vehicle transitions atomically to `IN_USE`.

#### 3.2.3. Safe Ride Completion (`finishRide`)
* Validates ride is `IN_PROGRESS`.
* Uses `findByIdForUpdate` on `Ride` and `Vehicle`.
* Computes final distance and cost. Sets ride `status = 'FINISHED'`, `finished_at = Instant.now(clock)`.
* Checks vehicle telemetry: if `batteryPercentage < 20`, transitions vehicle to `LOW_BATTERY`; otherwise transitions to `AVAILABLE`.

---

### 3.3. Transactional Outbox Pattern & Event Envelope (Aligned with ADR 0007 & EVENTS.md)

#### 3.3.1. Storage Architecture: Outbox Append-Only + Publications
To respect the immutability mandate of ADR 0007 and `EVENTS.md`:
* `outbox_event`: **Append-only** table containing events generated within the application transaction.
* `event_publication`: Records successful publication attempts without mutating `outbox_event`.
* A query for pending events retrieves records from `outbox_event` that have no entry in `event_publication` (or where publication failed and `next_attempt_at <= NOW()`).

```sql
CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    event_version INT NOT NULL,
    schema VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    source VARCHAR(100) NOT NULL,
    producer VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    causation_id VARCHAR(100),
    destination VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS event_publication (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES outbox_event(id),
    status VARCHAR(20) NOT NULL, -- 'PUBLISHED', 'FAILED', 'DEAD_LETTER'
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_pending_relay ON event_publication (status, next_attempt_at) 
WHERE status IN ('PENDING', 'FAILED');
```

#### 3.3.2. Standard Event Envelope (EVENTS.md Compliance)
All published events follow the JSON contract:
```json
{
  "eventId": "2f1c9e6a-7c2b-4f3e-9a1d-5b8c0d2e4f60",
  "eventType": "VehicleReserved",
  "eventVersion": 1,
  "schema": "https://urbanmove.local/schemas/VehicleReserved.v1.json",
  "occurredAt": "2026-11-20T22:30:00Z",
  "recordedAt": "2026-11-20T22:30:01Z",
  "source": "vehicle-456",
  "producer": "reservations",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "causationId": null,
  "payload": {
    "reservationId": "...",
    "vehicleId": "...",
    "userId": "..."
  }
}
```
* **Event Naming**: Uses business contract names (`VehicleReserved`, `TripRecorded`, `ReservationExpired`), not Java class names.
* **Producer**: Set to logical bounded contexts (`reservations`, `rides`), so future microservice splitting will not alter the contract.
* **Correlation Tracking**: A servlet filter captures or generates `X-Correlation-Id`, places it in SLF4J MDC, and populates `correlationId` in all emitted events.

#### 3.3.3. Outbox Relay Execution & Error Handling
* **Non-Blocking I/O**: The relay claims a batch of events in a short transaction, releases database locks, performs the SQS network I/O, and subsequently records publication results in a separate transaction.
* **Exponential Backoff**: When SQS delivery fails, `next_attempt_at` is calculated as `NOW() + (base_delay * 2^retry_count)`.
* **Dead Letter Escalation**: After 5 failed attempts, the publication is marked `DEAD_LETTER` to prevent hot loops, and an alert log is emitted.
* **Health / Observability Metric**: Spring Boot Actuator indicator exposing count of pending, failed, and dead-letter outbox events.

---

### 3.4. Complete State Machine & Transition Matrix

#### 3.4.1. Vehicle Lifecycle (9 States)
```
          ┌───────────────────┐
          │     AVAILABLE     │◄─────────────────────────┐
          └─┬───────────────┬─┘                          │
            │               │                            │
   Reserve  │               │ Walk-in                    │
            ▼               ▼                            │
      ┌──────────┐    ┌──────────┐                       │
      │ RESERVED │───►│  IN_USE  │                       │
      └─────┬────┘    └─────┬────┘                       │
            │               │                            │
     Expire │               │ Finish                     │
            │               ▼                            │
            │         (Check Battery)                    │
            │         ┌─────┴──────────┐                 │
            │         │                │                 │
            │     [Normal]        [Battery < 20%]        │
            │         │                │                 │
            │         │                ▼                 │
            │         │         ┌─────────────┐          │
            │         │         │ LOW_BATTERY │          │
            │         │         └──────┬──────┘          │
            │         │                │ Withdraw        │
            │         │                ▼                 │
            │         │         ┌───────────┐            │
            │         │         │ WITHDRAWN │            │
            │         │         └─────┬─────┘            │
            │         │               │ Charge / Repair  │
            │         │       ┌───────┴───────┐          │
            │         │       ▼               ▼          │
            │         │ ┌──────────┐    ┌───────────┐    │
            │         │ │ CHARGING │    │ REPAIRING │    │
            │         │ └─────┬────┘    └─────┬─────┘    │
            │         │       └───────┬───────┘          │
            │         │               │ Complete         │
            │         │               ▼                  │
            │         │       ┌───────────────────┐      │
            │         │       │ READY_FOR_SERVICE │──────┘
            │         │       └─────────┬─────────┘
            │         │                 │ Retire
            ▼         ▼                 ▼
          ┌──────────────┐        ┌─────────┐
          │  (AVAILABLE) │        │ RETIRED │
          └──────────────┘        └─────────┘
```

| Current State | Target State | Triggering Event / Action | Invariant Rules / Guards |
|---|---|---|---|
| `AVAILABLE` | `RESERVED` | `VehicleReserved` | Valid only if battery >= 20% and operable |
| `AVAILABLE` | `IN_USE` | `TripRecorded` (Walk-in) | Direct ride without reservation |
| `RESERVED` | `IN_USE` | `TripRecorded` (Reserved) | Reservation must be `ACTIVE` and not expired |
| `RESERVED` | `AVAILABLE` | `ReservationExpired` / `Cancelled` | Vehicle released back to public |
| `IN_USE` | `AVAILABLE` | `TripFinished` | Finished ride with battery >= 20% |
| `IN_USE` | `LOW_BATTERY`| `TripFinished` / `BatteryLow` | Finished ride with battery < 20% |
| `LOW_BATTERY` | `WITHDRAWN` | `VehicleWithdrawn` | Taken off street by maintenance operator |
| `WITHDRAWN` | `CHARGING` | `ChargeStarted` | Plugged in at station or depot |
| `WITHDRAWN` | `REPAIRING` | `RepairStarted` | Mechanical / electrical repair opened |
| `CHARGING` / `REPAIRING` | `READY_FOR_SERVICE` | `ServiceReady` | Completed charge / repair checklist |
| `READY_FOR_SERVICE` | `AVAILABLE` | `VehicleReturnedToService`| Redeployed to public street zone |
| Any non-IN_USE | `RETIRED` | `VehicleRetired` | Decommissioned; permanent terminal state |

---

### 3.5. Error Handling & RFC 7807 Mapping
Create a global `@RestControllerAdvice`:
* `OptimisticLockingFailureException`, `PessimisticLockException`, `DataIntegrityViolationException`:
  * Status: `409 Conflict`
  * Title: "Resource State Conflict"
  * Type: `https://urbanmove.local/errors/conflict`
  * Detail: "The vehicle is already reserved, locked, or undergoing a state transition."
* `VehicleNotFoundException`, `ReservationNotFoundException`:
  * Status: `404 Not Found`
* `InvalidStateTransitionException`:
  * Status: `422 Unprocessable Entity`
* `MethodArgumentNotValidException`:
  * Status: `400 Bad Request` with structured field errors.

---

## 4. Alternatives Considered

### 4.1. Concurrency Control: Optimistic Locking (`@Version`) vs. Pessimistic Write Lock (`PESSIMISTIC_WRITE`)
* **Alternative 1: Optimistic Locking with `@Version` on Vehicle**
  * *Mechanism*: Add `@Version private Long version;` to `VehicleJpaEntity` and domain record.
  * *Rejected by author*: The repository uses immutable domain records (`Vehicle`) mapped via `VehicleJpaEntity.fromDomain(v)`. Reconstructing new entity instances across domain boundaries causes Hibernate to treat entities as detached or requires polluting pure domain records with persistence version tokens. Furthermore, under high contention, optimistic locking requires client or service-level retry loops, whereas pessimistic write lock blocks briefly at the database level and yields immediate deterministic resolution.
* **Alternative 2: Distributed Lock with Redis / Redlock**
  * *Mechanism*: Acquire a lock key `lock:vehicle:{id}` in Redis prior to touching PostgreSQL.
  * *Rejected by author*: Introduces an external infrastructure dependency (Redis) into the operational path of the core fleet, increasing operational failure modes and contradicting the local-first simplicity of ADR 0001.

### 4.2. Outbox Architecture: Single Mutable Table vs. Append-Only Event + Publication Log
* **Alternative 1: Single Mutable `outbox_events` Table with `UPDATE status = 'PUBLISHED'`**
  * *Mechanism*: Single table where worker polls `WHERE status = 'PENDING'` and mutates rows to `PUBLISHED` upon successful SQS delivery.
  * *Rejected by author*: Direct contradiction with ADR 0007 and `EVENTS.md`, which mandate immutable event persistence. In high-throughput scenarios, mutating the outbox table generates PostgreSQL table bloat (WAL churn) and destroys the audit history of dispatch latencies and retry failures.

### 4.3. Reservation Slots: Two-Phase (`PENDING` -> `ACTIVE`) vs. Single-Phase (`ACTIVE` + `expires_at`)
* **Alternative 1: Two-Phase Reservation with `PENDING`**
  * *Mechanism*: Create reservation in `PENDING`, require subsequent `/confirm` call to move to `ACTIVE`.
  * *Rejected by author*: UrbanMove has no external payment gateway or asynchronous verification in the MVP. Introducing `PENDING` either invalidates the partial index `WHERE status = 'ACTIVE'` (allowing duplicate holds) or forces the index to check `IN ('PENDING', 'ACTIVE')`, adding unnecessary state transition complexity without functional benefit.

---

## 5. Detailed Implementation Plan

The remediation will be executed in three coherent phases:

### Phase P0.1: Concurrency, Expiration, and Ride Idempotency

#### Database Schema & Migration
* `services/core-fleet/src/main/resources/db/migration/V2__concurrency_and_expiration.sql`:
  * *Rationale*: Cleans up existing reservations, adds `expires_at` column, creates `uq_active_reservation_per_vehicle` partial index, adds `uq_ride_reservation` and `uq_active_ride_per_vehicle`, and makes `rides.reservation_id` nullable.

#### Domain Model & Port Updates
* `services/core-fleet/src/main/java/com/urbanmove/fleet/domain/model/Reservation.java`:
  * *Rationale*: Adds `expires_at` field and `isExpired(Instant now)` logic.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/domain/model/Ride.java`:
  * *Rationale*: Allows `null` reservationId for walk-ins and adds status guard methods.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/domain/port/out/VehicleRepositoryPort.java`:
  * *Rationale*: Adds `findByIdForUpdate(UUID id)` method for concurrency control.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/domain/port/out/ReservationRepositoryPort.java`:
  * *Rationale*: Adds `findActiveExpired(Instant now)` method.

#### Infrastructure & Application Services
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/out/persistence/postgres/repository/SpringDataVehicleRepository.java`:
  * *Rationale*: Implements `@Lock(LockModeType.PESSIMISTIC_WRITE)` query.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/out/persistence/postgres/repository/SpringDataReservationRepository.java`:
  * *Rationale*: Implements query for expired active reservations.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/out/persistence/postgres/adapter/PostgresVehicleRepositoryAdapter.java`:
  * *Rationale*: Exposes `findByIdForUpdate` mapping.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/application/service/FleetApplicationService.java`:
  * *Rationale*: Uses `findByIdForUpdate` on reservation creation, validates vehicle state, and implements walk-in vs. reserved validation in `startRide` and `finishRide`.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/application/service/ReservationExpirationScheduler.java`:
  * *Rationale*: Scheduled background job that releases expired reservations and restores vehicle availability.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/in/web/GlobalFleetExceptionHandler.java`:
  * *Rationale*: Handles `PessimisticLockException`, `DataIntegrityViolationException`, and `IllegalArgumentException`, formatting into RFC 7807 `ProblemDetail`.

#### Integration Tests
* `services/core-fleet/src/test/java/com/urbanmove/fleet/concurrency/FleetConcurrencyIntegrationTest.java`:
  * *Rationale*: Multithreaded integration test verifying that competing concurrent reservation requests yield exactly one 201 and N-1 409 Conflict responses.

---

### Phase P0.2: Outbox Pattern, Event Envelope & SQS Resilience

#### Database Schema
* `services/core-fleet/src/main/resources/db/migration/V3__outbox_and_events.sql`:
  * *Rationale*: Creates `outbox_event` and `event_publication` tables with appropriate indices.

#### Domain Events & Outbox Infrastructure
* `services/core-fleet/src/main/java/com/urbanmove/fleet/domain/event/DomainEventEnvelope.java`:
  * *Rationale*: Defines the standard JSON schema envelope (`eventId`, `eventType`, `eventVersion: 1`, `schema`, `occurredAt`, `recordedAt`, `source`, `producer`, `correlationId`, `causationId`, `payload`).
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/out/persistence/postgres/entity/OutboxEventJpaEntity.java`:
  * *Rationale*: Maps `outbox_event` table.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/out/persistence/postgres/entity/EventPublicationJpaEntity.java`:
  * *Rationale*: Maps `event_publication` table.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/out/messaging/outbox/OutboxEventPublisherAdapter.java`:
  * *Rationale*: Replaces direct SQS calls in the domain transaction by persisting to `outbox_event`.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/out/messaging/outbox/OutboxRelayScheduler.java`:
  * *Rationale*: Polls pending events, invokes SQS outside the database transaction, updates publication log with exponential backoff and dead-letter tracking.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/in/web/filter/CorrelationIdFilter.java`:
  * *Rationale*: Intercepts incoming HTTP requests to bind `X-Correlation-Id` to MDC and thread context.
* `services/core-fleet/src/main/java/com/urbanmove/fleet/infrastructure/out/messaging/outbox/OutboxHealthIndicator.java`:
  * *Rationale*: Exposes Actuator metrics for pending, failed, and dead-letter outbox events.

#### Resilience Tests
* `services/core-fleet/src/test/java/com/urbanmove/fleet/messaging/OutboxResilienceIntegrationTest.java`:
  * *Rationale*: Verifies events are not dropped when SQS is unreachable, testing relay retry and eventual delivery upon queue restoration.

---

### Phase P0.3: Complete State Machine & Documentation Reconciliation

#### State Machine Completion
* `services/core-fleet/src/main/java/com/urbanmove/fleet/domain/model/VehicleStatus.java`:
  * *Rationale*: Expands to all 9 lifecycle states (`AVAILABLE`, `RESERVED`, `IN_USE`, `LOW_BATTERY`, `WITHDRAWN`, `CHARGING`, `REPAIRING`, `READY_FOR_SERVICE`, `RETIRED`).
* `services/core-fleet/src/main/java/com/urbanmove/fleet/domain/model/Vehicle.java`:
  * *Rationale*: Adds transition guard methods corresponding to the state transition matrix.
* `services/core-fleet/src/main/resources/db/migration/V4__complete_vehicle_state_machine.sql`:
  * *Rationale*: Adds CHECK constraints for all 9 states and updates seed data.

#### Documentation Synchronization
* `README.md`:
  * *Rationale*: Accurately describes the operational status of `core-fleet`, Docker Compose setup, and test execution.
* `docs/00-gestion/PLAN.md`:
  * *Rationale*: Updates deliverables and checklist progress.
* `docs/00-gestion/ACTION-PLAN.md`:
  * *Rationale*: Aligns remaining phases with P1 and P2 priorities.
* `docs/02-desarrollo/MANUAL-PRUEBAS-MVP.md`:
  * *Rationale*: Documents the concurrency verification commands and updated curl payloads.
* `docs/01-arquitectura/adr/0007-event-sourcing-raw-trusted-curated.md`:
  * *Rationale*: Harmonizes with the immutable outbox design (`outbox_event` + `event_publication`).
* `docs/01-arquitectura/adr/0008-mvp-ver-y-reservar-hexagonal.md`:
  * *Rationale*: Details the concurrency defense-in-depth model.

---
