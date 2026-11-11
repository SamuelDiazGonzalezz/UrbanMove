# Event sourcing y capas RAW / TRUSTED / CURATED

> **Estado:** propuesta para revisión del equipo.
> **Última actualización:** 2026-11-11.
> **Ámbito:** cómo se guarda el estado como eventos inmutables, cómo se construyen las
> proyecciones y cómo se separan las capas del Data Lake.
> **Decisiones relacionadas:** [ADR 0007](adr/0007-event-sourcing-raw-trusted-curated.md).
> **Documentos relacionados:** [EVENTS.md](EVENTS.md) (catálogo), [DATA-MODEL.md](DATA-MODEL.md)
> (relaciones), [DATA-DICTIONARY.md](DATA-DICTIONARY.md) (atributos),
> [DATA-ARCHITECTURE.md](DATA-ARCHITECTURE.md) (S3 y marts) y
> [SERVICE-CLASSIFICATION.md](SERVICE-CLASSIFICATION.md) (OLTP/OLAP).

---

## 0. Decisión en una página

1. **La verdad es el evento.** Cada servicio tiene un `event_store` append-only.
2. **No hay `UPDATE` ni `DELETE` de negocio.** Corregir es emitir otro evento.
3. **El estado actual es una proyección.** Tablas append-only + vista `*_current`.
4. **Los snapshots son opcionales y desechables.** No son fuente de verdad.
5. **La publicación también es append-only.** `outbox_event` + `event_publication`.
6. **RAW es el hecho físico.** `VehiclePickedUp` y `VehicleDroppedOff`.
7. **TRUSTED es el viaje.** `trip` y su evento `TripRecorded`.
8. **CURATED son los marts.** Agregados por zona, hora y tipo.
9. **Los servicios consumen eventos del bus, no leen S3 `raw/`.**
10. **Keycloak es la excepción:** sistema externo gestionado; no se event-source.

---

## 1. Por qué event sourcing

| Problema | Solución |
|---|---|
| "¿Cómo estaba la bici ayer?" | Se replaya su stream |
| "¿Por qué se canceló la reserva?" | El evento lo dice |
| "El informe no cuadra" | Se reconstruyen los marts desde RAW |
| "Dos usuarios reservaron la misma bici" | Versión esperada + slot por vehículo |
| "Se perdió una proyección" | Se trunca y se reconstruye |

La inmutabilidad no es un adorno: permite auditar, reconstruir y explicar. El coste es
más almacenamiento y consultas de estado a través de vistas.

---

## 2. `event_store`

```sql
CREATE TABLE event_store (
  global_position   BIGSERIAL PRIMARY KEY,
  event_id          UUID        NOT NULL UNIQUE,
  aggregate_type    TEXT        NOT NULL,
  aggregate_id      UUID        NOT NULL,
  aggregate_version INT         NOT NULL,
  event_type        TEXT        NOT NULL,
  event_version     INT         NOT NULL,
  schema_uri        TEXT,
  occurred_at       TIMESTAMPTZ NOT NULL,
  recorded_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  correlation_id    UUID,
  causation_id      UUID,
  actor_id          UUID,
  payload           JSONB       NOT NULL,
  metadata          JSONB       NOT NULL DEFAULT '{}',
  UNIQUE (aggregate_id, aggregate_version)
);
```

Reglas:

- `aggregate_version` empieza en 1 y sube de uno en uno.
- `UNIQUE (aggregate_id, aggregate_version)` evita dos comandos concurrentes.
- `global_position` da el orden de replay.
- `occurred_at` es el tiempo del hecho; `recorded_at`, el de persistencia.
- Un evento nunca se actualiza ni se borra.

---

## 3. Streams por servicio

| Servicio | Stream | Eventos |
|---|---|---|
| `zones` | `zone-<zoneId>` | `ZoneCreated`, `ZoneRestrictionAdded`, `ZoneDeactivated` |
| `identity` | `user-<userId>` | `UserRegistered`, `UserRoleAssigned`, `UserDisabled` |
| `user-profiles` | `profile-<userId>` | `UserProfileCreated`, `UserProfileUpdated` |
| `inventory` | `vehicle-<vehicleId>` | `VehicleModelRegistered`, `VehiclePurchased`, `VehicleRetired` |
| `availability` | `vehicle-availability-<vehicleId>` | `VehicleAvailabilityChanged` |
| `reservations` | `reservation-<id>` y `vehicle-reservation-<vehicleId>` | `ReservationRequested`, `ReservationConfirmed`, `ReservationCancelled`, `ReservationExpired`, `ReservationCompleted`, `ReservationSlotHeld`, `ReservationSlotReleased` |
| `rides` | `trip-<tripId>` | `TripRecorded` |
| `maintenance` | `maintenance-order-<id>` | `MaintenanceOrderOpened`, `MaintenanceTaskAdded`, `MaintenanceOrderCompleted` |
| `telemetry` | `telemetry-<vehicleId>` | `TelemetryReceived`, `VehicleBatteryLow` |
| `incidents` | `incident-<id>` | `IncidentReported`, `IncidentResolved` |
| `relocation` | `relocation-order-<id>` | `RelocationOrderCreated`, `RelocationItemMoved`, `RelocationOrderCompleted` |
| `history` | — (consumidor) | — |
| `analytics` | — (consume S3) | `MartBuildCompleted` |

`availability` no guarda los eventos de otros servicios como verdad. Consume
`VehiclePickedUp`, `VehicleDroppedOff`, `VehicleReserved`, etc. y emite su propio
`VehicleAvailabilityChanged` con `fromState`, `toState`, `zoneId`, `reservationId`,
`rideId`, `reason` y `sourceEventId`.

---

## 4. Proyecciones y vistas `current`

Cada proyección es append-only:

```sql
CREATE TABLE vehicle_availability (
  projection_id  UUID PRIMARY KEY,
  vehicle_id     UUID NOT NULL,
  version        INT  NOT NULL,
  state          TEXT NOT NULL,
  zone_id        UUID,
  reservation_id UUID,
  trip_id        UUID,
  battery_pct    INT,
  valid_from     TIMESTAMPTZ NOT NULL,
  UNIQUE (vehicle_id, version)
);

CREATE VIEW vehicle_availability_current AS
SELECT DISTINCT ON (vehicle_id)
       vehicle_id, version, state, zone_id, reservation_id, trip_id, battery_pct, valid_from
FROM vehicle_availability
ORDER BY vehicle_id, version DESC;
```

- `INSERT` de una versión nueva por cada evento.
- La vista devuelve la última.
- Reconstruir: `TRUNCATE` de la proyección y replay del `event_store`.
- Si se quiere pureza total, se reconstruye en una tabla nueva y se cambia la vista.

---

## 5. Snapshots

```sql
CREATE TABLE aggregate_snapshot (
  snapshot_id       UUID PRIMARY KEY,
  aggregate_type    TEXT NOT NULL,
  aggregate_id      UUID NOT NULL,
  aggregate_version INT  NOT NULL,
  state             JSONB NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (aggregate_id, aggregate_version)
);
```

- Se crean cada N eventos si el stream es largo.
- Se pueden borrar y regenerar.
- **Nunca sustituyen al event store.**

---

## 6. Publicación: outbox + publication

```sql
CREATE TABLE outbox_event (
  event_id    UUID PRIMARY KEY REFERENCES event_store(event_id),
  destination TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE event_publication (
  publication_id UUID PRIMARY KEY,
  event_id       UUID NOT NULL,
  destination    TEXT NOT NULL,
  published_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (event_id, destination)
);
```

El relay:

1. Busca eventos en `outbox_event` sin fila en `event_publication`.
2. Publica en EventBridge/SQS.
3. Inserta una fila en `event_publication`.

No hay `UPDATE` de estado. Si falla, reintenta; la publicación es idempotente por
`(event_id, destination)`.

---

## 7. Concurrencia y doble reserva

Dos mecanismos:

1. **Stream por vehículo** en `reservations`: `vehicle-reservation-<vehicleId>`.
   `ReservationSlotHeld` y `ReservationSlotReleased` serializan las reservas de esa
   bici.
2. **Versión esperada** en `event_store`: si dos comandos leen la versión 7 y ambos
   intentan escribir la 8, uno falla y se reintenta.

No se usa un `UPDATE ... WHERE status = 'ACTIVE'` ni un índice único parcial sobre una
tabla mutable. La regla vive en el agregado.

---

## 8. Capas del Data Lake

| Capa | Grano | Contenido | Formato | Recalculable |
|---|---|---|---|---|
| **RAW** | Evento | Pickup, dropoff, telemetría | JSON | No; es el original |
| **TRUSTED** | Viaje / entidad | `trip`, zonas, duración, distancia | Parquet | Sí, desde RAW |
| **CURATED** | Agregado | `mart_viajes`, `mart_demanda`… | Parquet | Sí, desde TRUSTED |

**Regla:** si una fila mezcla dos hechos físicos, ya no es RAW. Si agrega muchos viajes,
es CURATED.

---

## 9. RAW: pickup y dropoff

### 9.1. `VehiclePickedUp`

```json
{
  "eventId": "01J8Z3K4M5N6P7Q8R9S0T1U2V3",
  "eventType": "VehiclePickedUp",
  "eventVersion": 1,
  "schema": "https://urbanmove.local/schemas/VehiclePickedUp.v1.json",
  "occurredAt": "2026-11-11T18:10:00Z",
  "recordedAt": "2026-11-11T18:10:03Z",
  "source": "vehicle-456",
  "producer": "ingestion-service",
  "correlationId": "c1d2e3f4-...",
  "payload": {
    "vehicleId": "vehicle-456",
    "userId": "user-123",
    "latitude": 39.4699,
    "longitude": -0.3763,
    "batteryPct": 87,
    "odometerKm": 120.4
  }
}
```

### 9.2. `VehicleDroppedOff`

```json
{
  "eventId": "01J8Z4A1B2C3D4E5F6G7H8J9K0",
  "eventType": "VehicleDroppedOff",
  "eventVersion": 1,
  "schema": "https://urbanmove.local/schemas/VehicleDroppedOff.v1.json",
  "occurredAt": "2026-11-11T18:30:00Z",
  "recordedAt": "2026-11-11T18:30:04Z",
  "source": "vehicle-456",
  "producer": "ingestion-service",
  "correlationId": "c1d2e3f4-...",
  "payload": {
    "vehicleId": "vehicle-456",
    "userId": "user-123",
    "latitude": 39.4620,
    "longitude": -0.3540,
    "batteryPct": 64,
    "odometerKm": 123.1
  }
}
```

Reglas de RAW:

- **No hay `tripId`.** El viaje se genera en TRUSTED.
- **No hay `zoneId`.** La zona se deriva de las coordenadas.
- **No hay duración ni coste.** Son derivados.
- `batteryPct` y `odometerKm` son opcionales: no todas las bicis son eléctricas ni
  tienen odómetro.
- `occurredAt` es el tiempo del dispositivo; `recordedAt`, el de ingesta.
- `eventVersion` es la versión del esquema; `schema` apunta al JSON Schema.

### 9.3. Versión de esquema

| Cambio | ¿Sube `eventVersion`? |
|---|---|
| Añadir un campo opcional | No |
| Quitar un campo | Sí |
| Cambiar tipo o significado | Sí |
| Añadir un valor a un enum | No, si el consumidor lo tolera |

Cada versión vive en `contracts/events/<EventType>.v<n>.json`. Los consumidores ignoran
campos desconocidos.

---

## 10. TRUSTED: el viaje

### 10.1. Correlación

Para cada `vehicleId`, ordenado por `occurredAt`:

```text
VehiclePickedUp(18:10) ──┐
                         ├──► TripRecorded
VehicleDroppedOff(18:30) ┘
```

Casos:

| Caso | Resultado |
|---|---|
| Pickup + dropoff | `trip` con `status = COMPLETED` |
| Dropoff sin pickup | `status = ORPHAN` |
| Pickup sin dropoff | `open_trip` con `status = OPEN` y alerta |
| Dos pickups seguidos | El anterior pasa a `SUSPECT` |
| Zona inválida | `ZONE_MISMATCH`; se usa la zona calculada |
| Reloj invertido | `SUSPECT` |

### 10.2. `TripRecorded`

```json
{
  "eventType": "TripRecorded",
  "eventVersion": 1,
  "payload": {
    "tripId": "trip-789",
    "vehicleId": "vehicle-456",
    "userId": "user-123",
    "origin": {
      "zoneId": "zone-centro",
      "latitude": 39.4699,
      "longitude": -0.3763,
      "occurredAt": "2026-11-11T18:10:00Z"
    },
    "destination": {
      "zoneId": "zone-playa",
      "latitude": 39.4620,
      "longitude": -0.3540,
      "occurredAt": "2026-11-11T18:30:00Z"
    },
    "durationSeconds": 1200,
    "distanceMeters": 2700,
    "batteryDropPct": 23,
    "preliminaryCost": 3.5,
    "status": "COMPLETED"
  }
}
```

- `tripId` se genera en TRUSTED.
- `durationSeconds`, `distanceMeters` y `preliminaryCost` son atributos del viaje.
- `distanceMeters` puede ser `null` si no hay odómetro ni GPS.
- `batteryDropPct` puede ser `null` si no es eléctrica.

### 10.3. Nombres

| Concepto | Nombre |
|---|---|
| Entidad / tabla | `trip` |
| Evento trusted | `TripRecorded` |
| Vista actual | `trip_current` |
| Proyección de viaje abierto | `open_trip` |

`TripCompleted` se descarta: describe una transición, no el hecho de que el viaje queda
registrado.

### 10.4. Mapeo con la rúbrica

| Evento de la rúbrica | Nuestro evento | Capa |
|---|---|---|
| `RideStarted` | `VehiclePickedUp` | RAW |
| `RideFinished` | `TripRecorded` | TRUSTED |

Así se cumple el catálogo sin inventar un `TripStarted` público.

---

## 11. S3

```text
s3://urbanmove-datalake/
├── raw/source=vehicle-456/event_type=VehiclePickedUp/year=2026/month=11/day=11/hour=18/<eventId>.json
├── raw/source=vehicle-456/event_type=VehicleDroppedOff/year=2026/month=11/day=11/hour=18/<eventId>.json
├── trusted/domain=trips/year=2026/month=11/day=11/part-0001.parquet
└── curated/domain=demand/year=2026/month=11/day=11/part-0001.parquet
```

| Prefijo | Formato | Escribe | Contenido |
|---|---|---|---|
| `raw/` | JSON | `analytics-ingest` | Evento original, sin transformar |
| `trusted/` | Parquet | Lambda de transformación | `trip` normalizado y deduplicado |
| `curated/` | Parquet | Lambda `mart-builder` | Agregados por dominio; también alimenta `analytics_db` |

Reglas:

- Un evento, un objeto en `raw/`; la clave usa `eventId`.
- No se sobrescribe un objeto.
- `trusted` y `curated` se regeneran desde la capa anterior.
- Nada de datos personales en eventos: solo IDs.

---

## 12. Marts append-only

```sql
CREATE TABLE mart_build (
  build_id     UUID PRIMARY KEY,
  mart_name    TEXT NOT NULL,
  window_start TIMESTAMPTZ NOT NULL,
  window_end   TIMESTAMPTZ NOT NULL,
  built_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  status       TEXT NOT NULL DEFAULT 'SUCCESS'
);

CREATE TABLE mart_viajes (
  build_id            UUID NOT NULL REFERENCES mart_build(build_id),
  date_key            DATE NOT NULL,
  hour                SMALLINT NOT NULL,
  origin_zone_id      UUID NOT NULL,
  destination_zone_id UUID NOT NULL,
  vehicle_type        TEXT NOT NULL,
  trips               BIGINT NOT NULL,
  avg_duration_seconds NUMERIC(10,2) NOT NULL,
  total_distance_meters BIGINT,
  revenue             NUMERIC(12,2) NOT NULL,
  PRIMARY KEY (build_id, date_key, hour, origin_zone_id, destination_zone_id, vehicle_type)
);

CREATE VIEW mart_viajes_current AS
SELECT m.*
FROM mart_viajes m
WHERE m.build_id = (
  SELECT build_id
  FROM mart_build
  WHERE mart_name = 'mart_viajes' AND status = 'SUCCESS'
  ORDER BY built_at DESC
  LIMIT 1
);
```

Cada build añade filas. Grafana lee `mart_viajes_current`. No hay `UPSERT`.

---

## 13. Replay y reconstrucción

```text
event_store ──► proyección
     │
     └──► snapshot (opcional)
```

Pasos:

1. `TRUNCATE` la proyección.
2. Leer `event_store` por `global_position`.
3. Aplicar el fold.
4. Verificar contra un snapshot o un checksum.

Si se perdió el event store, se reconstruye desde S3 `raw/`. Si se perdieron los marts,
se reconstruyen desde `trusted/` o desde el event store.

---

## 14. Consumo operativo frente a consumo analítico

| Consumidor | Qué consume | De dónde |
|---|---|---|
| `availability` | `VehiclePickedUp`, `VehicleDroppedOff` | Bus de eventos |
| `rides` | `VehiclePickedUp`, `VehicleDroppedOff` | Bus de eventos |
| `history` | Todos los eventos de negocio | Bus de eventos |
| `analytics-ingest` | Todos los eventos | Bus de eventos → S3 `raw/` |
| `mart-builder` | `trusted/` | S3 |
| Grafana | `mart_*_current` | `analytics_db` |

**Regla:** ningún servicio operacional lee `s3://.../raw/` en tiempo de ejecución. El bus
es para operar; S3 es para analizar y auditar.

---

## 15. Anti-patrones

| Anti-patrón | Por qué está mal |
|---|---|
| `UPDATE` del estado de una reserva | Rompe la inmutabilidad y la auditoría |
| `tripId` en RAW generado por el backend | Ya no es un hecho del dispositivo |
| `zoneId` en RAW sin validar | Duplica la verdad y puede ser erróneo |
| Servicio operacional leyendo S3 `raw/` | Acopla operación a analítica |
| Mart con `valor = valor + 1` | No es idempotente ni reconstruible |
| Publicar dentro de la transacción de negocio | Doble escritura no atómica |
| Borrar eventos "porque ocupan" | Se pierde la historia; se archiva en S3 |

---

## 16. Checklist

- [ ] Cada servicio tiene `event_store`, `outbox_event` y `processed_event`.
- [ ] Ninguna tabla de negocio se actualiza: se añade una versión.
- [ ] Las vistas `*_current` devuelven la última versión.
- [ ] `vehicle_availability` consume pickup/dropoff del bus, no de S3.
- [ ] `trip` se construye en TRUSTED y se publica como `TripRecorded`.
- [ ] RAW no tiene `tripId`, `zoneId`, duración ni coste.
- [ ] `batteryPct` y `odometerKm` son opcionales.
- [ ] Cada evento tiene `eventVersion` y `schema`.
- [ ] Los marts son append-only por `build_id`.
- [ ] Se puede reconstruir una proyección desde cero.
