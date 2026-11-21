# Estado y pendientes del MVP — 21 de noviembre de 2026

> Documento de situación para retomar el trabajo sin depender del historial de
> conversación. Describe qué está hecho y verificado, qué deuda queda y en qué
> orden conviene atacarla.
>
> **Fecha:** 2026-11-21
> **Rama:** `fase0-skeleton`
> **Último commit:** `05dca45`
> **Ámbito:** `core-fleet` (monolito modular: `availability`, `reservations`, `rides`)

---

## 1. Resumen en una frase

El MVP local arranca con Docker Compose y el flujo *reservar → viaje → evento en
SQS* funciona con concurrencia segura y pruebas en verde. Falta el tramo
SQS → Lambda → S3, la capa de outbox, la máquina de estados completa y toda la
documentación derivada.

---

## 2. Estado del entorno local

Verificado el 2026-11-21:

| Contenedor | Estado | Puerto |
|---|---|---|
| `urbanmove-postgres` | healthy | 5432 |
| `urbanmove-localstack` | healthy | 4566 |
| `urbanmove-core-fleet` | up | 8080 |

- Flyway aplica `V1` y `V2` (`uq_active_reservation_per_vehicle`, `expires_at`,
  `uq_ride_reservation`).
- UI en `http://localhost:8080/`, API en `http://localhost:8080/api/v1/...`.
- En LocalStack se crearon **a mano** la cola `trip-recorded-queue` y el bucket
  `s3://urbanmove-data-lake`. Se pierden con `docker compose down -v`.
- Herramientas del host: Java 21.0.2, Maven 3.9.16 en
  `E:\...\cn\apache-maven-3.9.16\bin`, Docker 29.9.3. **No hay** `terraform` ni
  `aws` CLI.

### 2.1. Aviso: Docker Engine 29 y Testcontainers

Docker 29 habla API 1.56 y la versión de docker-java que sombrea Testcontainers
1.19.8 pide 1.32 (eliminada). El síntoma es un `400` engañoso:
`Could not find a valid Docker environment`.

Solución local (fuera del repositorio, no versionar):

```properties
# C:\Users\<usuario>\.docker-java.properties
api.version=1.56
```

Los runners de CI con Docker < 29 no lo necesitan. Si CI actualiza a Docker 29,
habrá que subir Testcontainers o fijar `api.version` también allí.

---

## 3. Qué está hecho y verificado

### 3.1. Endurecimiento P0.1

- Bloqueo pesimista `findByIdForUpdate` (`PESSIMISTIC_WRITE`) en vehículo,
  reserva y viaje.
- Índice único parcial `uq_active_reservation_per_vehicle` y `expires_at` a
  15 minutos.
- `ReservationExpirationScheduler` que expira reservas y libera el vehículo.
- Idempotencia de viajes: `uq_ride_reservation` y `uq_active_ride_per_vehicle`;
  `reservation_id` nullable.
- `GlobalFleetExceptionHandler` con RFC 7807 (409/404/422/400).
- Servicios separados: `ReservationService`, `RideService`,
  `VehicleAvailabilityService`; `FleetApplicationService` solo delega.

### 3.2. Pruebas

`mvn -B -pl services/core-fleet -am test` → **10/10 en verde** (exit 0).

- `FleetConcurrencyIntegrationTest` usa el bean real de `ReservationService`
  (`@Autowired`) para que el proxy `@Transactional` active el bloqueo, clasifica
  conflictos con `instanceof` y comprueba en base de datos que queda exactamente
  una reserva `ACTIVE`.
- Se añadieron `spring-boot-testcontainers`, `testcontainers:junit-jupiter` y
  `testcontainers:postgresql`.

### 3.3. Historial reciente

```text
05dca45 test(core-fleet): exercise real ReservationService proxy in concurrency IT
90320b6 build(core-fleet): add Testcontainers test dependencies
f8569e5 fix: add getAllRides() and correct TripRecordedEvent constructor
7cce065 feat(core-fleet): implement P0.1 concurrency hardening, reservation expiration, ride idempotency, and modular services
731ff06 docs(design): add P0 critical remediation and concurrency hardening design document
```

Pendiente de push: 5 commits por delante de `origin/fase0-skeleton`.

---

## 4. Deuda pendiente de P0.1

1. **Scheduler mal particionado.** Todo el barrido va en un único
   `@Transactional` con `try/catch` por reserva; el primer fallo deja la
   transacción `rollback-only`. Cada reserva necesita su propia transacción
   (bean aparte o `TransactionTemplate`).
2. **El scheduler no emite `ReservationExpired`**, aunque el diseño lo exige.
   `cancelReservation` tampoco publica evento.
3. **Falta `FOR UPDATE SKIP LOCKED`** en `findExpiredActive`: dos instancias
   procesarían la misma reserva.
4. **Umbral de batería incoherente.** Se reserva con `>= 15` pero `finishRide`
   manda a `MAINTENANCE` con `< 20`. Decidir uno y alinear con el diseño
   (`LOW_BATTERY`).
5. **V2 cancela reservas `ACTIVE` duplicadas sin liberar sus vehículos**: puede
   quedar un vehículo `RESERVED` sin reserva activa.
6. **El Dockerfile compila tests con `-DskipTests`.** Funciona, pero
   `-Dmaven.test.skip=true` sería más rápido y menos frágil.
7. **CI.** `mvn -B -ntp clean test` ejecutará el IT de Testcontainers en
   `ubuntu-latest`. Verificar el primer run; si molesta, mover `*IT` a Failsafe
   con `mvn verify`.
8. **El diseño contradice el código**: `FINISHED` vs `COMPLETED`,
   `LOW_BATTERY` vs `MAINTENANCE`, 20 % vs 15 %, e `idx_outbox_pending_relay`
   filtrando `PENDING` (estado que nunca se inserta). El documento sigue marcado
   `Approved` aunque P0.2 y P0.3 no existen.

Referencia: [`p0-remediation-design.md`](../01-arquitectura/p0-remediation-design.md).

---

## 5. Pendiente P0.2 — Outbox y sobre de evento (sin empezar)

- Tablas `outbox_event` (append-only) y `event_publication` (sin `UPDATE`),
  alineadas con el [ADR 0007](../01-arquitectura/adr/0007-event-sourcing-raw-trusted-curated.md).
- Relay con claim por lotes (`SKIP LOCKED`), envío a SQS **fuera** de la
  transacción, backoff exponencial y `DEAD_LETTER` a los 5 intentos.
- Sobre estándar de [`EVENTS.md`](../01-arquitectura/EVENTS.md): `eventId`,
  `eventType` (`VehicleReserved`, `TripRecorded`, `ReservationExpired`),
  `eventVersion: 1`, `schema`, `occurredAt`, `recordedAt`, `source`,
  `producer` (`reservations`/`rides`), `correlationId`, `causationId`,
  `payload`.
- `CorrelationIdFilter` con `X-Correlation-Id` + MDC.
- Health indicator de outbox: pendientes, fallidos y muertos.
- Tests: SQS caído (la fila queda pendiente y se reintenta), entrega duplicada e
  idempotencia del consumidor.

---

## 6. Pendiente P0.3 — Máquina de estados y documentación

- 9 estados de `Vehicle`: `AVAILABLE`, `RESERVED`, `IN_USE`, `LOW_BATTERY`,
  `WITHDRAWN`, `CHARGING`, `REPAIRING`, `READY_FOR_SERVICE`, `RETIRED`;
  métodos de transición con guardas y `CHECK` en base de datos (migración V4).
- Sincronizar `README.md`, `PLAN.md`, `ACTION-PLAN.md`,
  `MANUAL-PRUEBAS-MVP.md`, `EVENTS.md`, ADR 0007, ADR 0008, `DATA-MODEL.md`,
  `DATA-DICTIONARY.md`, `CONTAINERS.md` y `CI-CD.md`.
- Corregir el design doc o bajarlo a `Partially implemented`.

---

## 7. Flujo end-to-end incompleto

Lo que funciona: reserva → 201 → evento en SQS → `GET /api/v1/rides` → UI.
Doble reserva sobre el mismo vehículo → 409 `ProblemDetail`.

Lo que **no** funciona: **SQS → Lambda → S3**. La Lambda `analytics-ingest` y su
Event Source Mapping los gestiona Terraform y no se ha aplicado, así que el
mensaje se queda en la cola y `raw/` está vacío.

Opciones para cerrarlo:

1. Instalar Terraform y aplicar `infra/terraform` (camino previsto).
2. Desplegar a mano con `awslocal lambda create-function` +
   `create-event-source-mapping` (parche rápido, no reproducible).

---

## 8. Pendiente P1 / P2 (resumen)

- **P1:** Keycloak + Spring Security + roles (`identity`), contratos
  OpenAPI/AsyncAPI/JSON Schema en `contracts/`, split de `core-fleet` en
  `availability`/`reservations`/`rides`, servicios restantes (`identity`,
  `user-profiles`, `inventory`, `history`, `maintenance`, `relocation`,
  `analytics`), data lake `trusted/` + `curated/` con marts,
  Grafana/Prometheus, Lambdas `reservation-expiration`,
  `maintenance-detection`, `mart-builder` y `demand-relocation`, recolocación
  por demanda.
- **P2:** workflows `integration.yml`, `cd.yml`, `infra.yml`, `security.yml`,
  branch protection, Dependabot; contenedor non-root + `JAVA_OPTS` + probes +
  límites; AWS Academy real; resiliencia y Locust; entregables.

---

## 9. Orden recomendado para retomar

1. Verificar `mvn -pl services/core-fleet -am test` y atacar la deuda 1-4 de la
   sección 4 (scheduler y umbral de batería).
2. Implementar P0.2 completo con sus tests.
3. Desplegar la Lambda vía Terraform para cerrar SQS → S3 y documentarlo.
4. P0.3: estados del vehículo y sincronización de documentación.
5. Empezar P1 solo cuando `core-fleet` tenga contrato, outbox, idempotencia,
   test de integración y README.

**Regla:** no abrir un servicio nuevo hasta que `core-fleet` cumpla esos cinco
requisitos.

---

## 10. Comandos útiles

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f core-fleet
docker compose down          # conserva volúmenes
docker compose down -v       # borra Postgres y LocalStack

mvn -B -pl services/core-fleet -am test
mvn -B -pl services/core-fleet clean package -DskipTests

# Verificación manual
curl.exe -s http://localhost:8080/api/v1/vehicles
curl.exe -s http://localhost:8080/api/v1/rides

# LocalStack
docker exec urbanmove-localstack awslocal sqs list-queues
docker exec urbanmove-localstack awslocal s3 ls
docker exec urbanmove-localstack awslocal s3 ls s3://urbanmove-data-lake --recursive
```
