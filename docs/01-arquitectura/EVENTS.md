# Catálogo de eventos

> **Estado:** propuesta para revisión del equipo.
> **Última actualización:** 2026-11-11.
> **Ámbito:** sobre común, catálogo de eventos por capa, versionado, publicación e
> idempotencia.
> **Decisión de fondo:** [ADR 0007](adr/0007-event-sourcing-raw-trusted-curated.md).
> **Documentos relacionados:** [EVENT-SOURCING.md](EVENT-SOURCING.md) (event store y
> proyecciones), [DATA-MODEL.md](DATA-MODEL.md) (relaciones),
> [DATA-DICTIONARY.md](DATA-DICTIONARY.md) (atributos) y
> [DATA-ARCHITECTURE.md](DATA-ARCHITECTURE.md) (S3, RDS y marts).

---

## 0. Idea clave

Un evento es un **hecho ocurrido**, no una orden ni un estado. Se nombra en pasado, no
se modifica y no se borra. La capa a la que pertenece depende de **cuánta interpretación
contiene**:

| Capa | Contiene | Ejemplo | ¿Se puede recalcular? |
|---|---|---|---|
| **RAW** | El hecho físico, sin interpretar | `VehiclePickedUp` | No: es el original |
| **TRUSTED** | El hecho correlacionado y validado | `TripRecorded` | Sí, desde RAW |
| **CURATED** | El agregado para decidir | `MartBuildCompleted` | Sí, desde TRUSTED |

**Regla:** si un evento necesita saber "cuál es el viaje", ya no es RAW.

---

## 1. Sobre común

Todos los eventos usan el mismo sobre:

```json
{
  "eventId": "2f1c9e6a-7c2b-4f3e-9a1d-5b8c0d2e4f60",
  "eventType": "VehiclePickedUp",
  "eventVersion": 1,
  "schema": "https://urbanmove.local/schemas/VehiclePickedUp.v1.json",
  "occurredAt": "2026-11-11T18:10:00Z",
  "recordedAt": "2026-11-11T18:10:03Z",
  "source": "vehicle-456",
  "producer": "ingestion-service",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "causationId": "0f9e8d7c-6b5a-4321-9876-543210fedcba",
  "payload": {
    "vehicleId": "vehicle-456",
    "userId": "user-789",
    "latitude": 39.4699,
    "longitude": -0.3763,
    "batteryPct": 87,
    "odometerKm": 120.4
  }
}
```

| Campo | Obligatorio | Descripción |
|---|---|---|
| `eventId` | Sí | UUID único del evento; base de la idempotencia |
| `eventType` | Sí | PascalCase, en pasado: `VehiclePickedUp` |
| `eventVersion` | Sí | Versión del **esquema** del payload |
| `schema` | Sí | URI del JSON Schema que valida el payload |
| `occurredAt` | Sí | Cuándo ocurrió el hecho, ISO-8601 UTC |
| `recordedAt` | Sí | Cuándo lo registró el productor |
| `source` | Sí | Origen físico o lógico (`vehicle-456`, `reservations-service`) |
| `producer` | Sí | Nombre lógico del productor |
| `correlationId` | Sí | Traza del flujo completo entre servicios |
| `causationId` | No | `eventId` del evento que provocó este |
| `payload` | Sí | Datos específicos del evento |

Diferencias con el diseño anterior:

- Se añade **`schema`**, porque `eventVersion` solo no dice qué contrato validar.
- Se añade **`recordedAt`**, porque `occurredAt` es el tiempo del dispositivo y puede
  llegar tarde o con reloj desajustado.
- `eventVersion` es del **payload**, no del sobre. El sobre es estable.

---

## 2. Catálogo por capa

### 2.1. RAW: hechos físicos

| Evento | Productor | Consumidores | Disparador |
|---|---|---|---|
| `VehiclePickedUp` | `rides` (ingesta) | `availability`, `rides`, `history`, `analytics-ingest` | El usuario desbloquea el vehículo |
| `VehicleDroppedOff` | `rides` (ingesta) | `availability`, `rides`, `history`, `analytics-ingest` | El usuario devuelve el vehículo |

Payload de `VehiclePickedUp`:

```json
{
  "vehicleId": "vehicle-456",
  "userId": "user-123",
  "latitude": 39.4699,
  "longitude": -0.3763,
  "batteryPct": 87,
  "odometerKm": 120.4
}
```

Payload de `VehicleDroppedOff`:

```json
{
  "vehicleId": "vehicle-456",
  "userId": "user-123",
  "latitude": 39.4620,
  "longitude": -0.3540,
  "batteryPct": 64,
  "odometerKm": 123.1
}
```

Reglas de RAW:

| Regla | Motivo |
|---|---|
| **No hay `tripId`** | El viaje se genera en TRUSTED, al correlacionar |
| **No hay `zoneId`** | La zona se deriva de las coordenadas |
| **No hay duración, distancia ni coste** | Son derivados, no hechos |
| **`batteryPct` y `odometerKm` son opcionales** | No todos los vehículos son eléctricos ni tienen odómetro |
| **No hay datos personales** | Solo IDs; el correo y el teléfono viven en su servicio |

### 2.2. TRUSTED: hechos correlacionados

| Evento | Productor | Consumidores | Disparador |
|---|---|---|---|
| `TripRecorded` | `rides` | `history`, `analytics-ingest`, marts | Pickup + dropoff correlacionados |

Payload de `TripRecorded`:

```json
{
  "tripId": "trip-789",
  "vehicleId": "vehicle-456",
  "userId": "user-123",
  "reservationId": "reservation-321",
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
```

- `tripId` se genera en TRUSTED.
- `durationSeconds`, `distanceMeters` y `preliminaryCost` son atributos del viaje.
- `distanceMeters` puede ser `null` si no hay odómetro ni GPS.
- `batteryDropPct` puede ser `null` si el vehículo no es eléctrico.

### 2.3. Eventos de otros servicios

Estos eventos son de negocio, pero no son hechos físicos. Actúan como disparadores y
como historia del agregado.

| Evento | Productor | Consumidores | Capa |
|---|---|---|---|
| `UserRegistered` | `identity` | `user-profiles`, `history`, `analytics-ingest` | Dominio |
| `UserProfileUpdated` | `user-profiles` | `history`, `analytics-ingest` | Dominio |
| `ZoneCreated` | `zones` | `availability`, `history`, `analytics-ingest` | Dominio |
| `VehiclePurchased` | `inventory` | `availability`, `history`, `analytics-ingest` | Dominio |
| `VehicleRetired` | `inventory` | `availability`, `history`, `analytics-ingest` | Dominio |
| `VehicleReserved` | `reservations` | `availability`, `rides`, `history`, `analytics-ingest` | Dominio |
| `ReservationCancelled` | `reservations` | `availability`, `history`, `analytics-ingest` | Dominio |
| `ReservationExpired` | `reservations` / Lambda | `availability`, `history`, `analytics-ingest` | Dominio |
| `VehicleAvailabilityChanged` | `availability` | `reservations`, `rides`, `history`, `analytics-ingest` | Dominio |
| `VehicleBatteryLow` | `telemetry` / Lambda | `maintenance`, `availability`, `history`, `analytics-ingest` | Dominio |
| `VehicleWithdrawnForMaintenance` | `maintenance` | `availability`, `history`, `analytics-ingest` | Dominio |
| `VehicleReturnedToService` | `maintenance` | `availability`, `history`, `analytics-ingest` | Dominio |
| `IncidentReported` | `incidents` | `maintenance`, `history`, `analytics-ingest` | Dominio |
| `MaintenanceOrderOpened` | `maintenance` | `history`, `analytics-ingest` | Dominio |
| `MaintenanceOrderCompleted` | `maintenance` | `history`, `analytics-ingest` | Dominio |
| `DemandHotspotDetected` | Lambda de demanda | `relocation`, `history`, `analytics-ingest` | Dominio |
| `RelocationOrderCreated` | `relocation` | `history`, `analytics-ingest`, marts | Dominio |
| `RelocationOrderCompleted` | `relocation` | `history`, `analytics-ingest`, marts | Dominio |
| `TelemetryReceived` | `telemetry` | `maintenance`, `analytics-ingest` | Dominio |
| `MartBuildCompleted` | `analytics` | Grafana, operaciones | CURATED |

> **Eventos internos y eventos publicados.** El `event_store` guarda cada transición del
> agregado (`ReservationRequested`, `ReservationSlotHeld`, `ReservationConfirmed`…). El
> bus publica un contrato estable y más pequeño (`VehicleReserved`, `ReservationCancelled`…).
> La traducción ocurre en el adaptador de salida del servicio. Así el contrato público no
> se rompe cuando cambia una transición interna. El detalle de streams está en
> [EVENT-SOURCING.md](EVENT-SOURCING.md), sección 3.

### 2.4. Mapeo con la rúbrica

| Evento de la rúbrica | Evento del proyecto | Capa |
|---|---|---|
| `RideStarted` | `VehiclePickedUp` | RAW |
| `RideFinished` | `TripRecorded` | TRUSTED |

**No se publica `TripStarted`**: el pickup ya lo dice. Tampoco se publica
`TripCompleted`: la entidad es `trip` y el evento es `TripRecorded`, porque describe el
hecho de quedar registrado, no una transición de estado.

---

## 3. Versionado

| Cambio | ¿Sube `eventVersion`? |
|---|---|
| Añadir un campo opcional | No |
| Añadir un valor a un enum tolerado por el consumidor | No |
| Quitar un campo | Sí |
| Cambiar tipo o significado | Sí |
| Renombrar un campo | Sí |

Reglas:

- Cada versión tiene su JSON Schema en `contracts/events/<EventType>.v<n>.json`.
- El campo `schema` del sobre apunta a ese fichero.
- Un consumidor **ignora** los campos desconocidos.
- El productor mantiene la versión anterior durante un periodo de compatibilidad.
- El catálogo se publica con AsyncAPI 3 en `contracts/asyncapi/urbanmove.yaml`.

---

## 4. Publicación: outbox + publication

### 4.1. Garantía

Se asume **entrega al menos una vez** (at-least-once). Todo consumidor debe ser
idempotente.

### 4.2. Sin `UPDATE` de estado

```text
BEGIN;
  -- 1. el hecho
  INSERT INTO event_store (...);
  -- 2. la intención de publicar
  INSERT INTO outbox_event (event_id, destination, created_at) VALUES (...);
COMMIT;
```

El relay:

```text
1. SELECT de outbox_event sin fila en event_publication.
2. Publicar en EventBridge / SQS.
3. INSERT en event_publication (event_id, destination, published_at).
```

- No se marca `SENT` con un `UPDATE`.
- La publicación es idempotente por `UNIQUE (event_id, destination)`.
- Si el relay cae, al reiniciar reintenta lo no publicado.

> El diseño anterior usaba `outbox_event` con columna `status` y un `UPDATE a SENT`.
> Ese `UPDATE` se elimina: la verdad de "publicado" es la existencia de la fila en
> `event_publication`.

### 4.3. `event_store` no es `outbox_event`

| Tabla | Papel | Qué guarda |
|---|---|---|
| `event_store` | Verdad del agregado | Todos los eventos, con versión y payload |
| `outbox_event` | Cola de publicación | Solo el `event_id` y el destino |
| `event_publication` | Registro de publicaciones | Qué se publicó y cuándo |
| `processed_event` | Idempotencia de consumo | Qué consumió cada consumidor |

Así se puede reconstruir el estado (event store) sin depender de si el evento llegó a
publicarse (outbox).

### 4.4. Idempotencia del consumidor

```text
processed_event(event_id PRIMARY KEY, consumer, processed_at)
```

1. Intentar insertar `(event_id, consumer)`.
2. Si ya existe, ignorar el mensaje.
3. Si no existe, procesar en la misma transacción.

### 4.5. Reintentos y DLQ

- SQS reintenta con *visibility timeout*.
- Tras `maxReceiveCount`, el mensaje va a la DLQ.
- Una alarma avisa cuando la DLQ tiene mensajes.
- El reprocesamiento es manual o con una herramienta de replay.

### 4.6. Correlation ID

- Se genera en el edge o en el primer servicio que atiende la petición.
- Se propaga por HTTP (`X-Correlation-Id`) y por el sobre del evento.
- Se incluye en todos los logs.
- Permite reconstruir un flujo completo en Grafana/CloudWatch.

---

## 5. Ejemplo de JSON Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://urbanmove.local/schemas/VehiclePickedUp.v1.json",
  "title": "VehiclePickedUp",
  "type": "object",
  "required": ["vehicleId", "userId", "latitude", "longitude"],
  "properties": {
    "vehicleId": { "type": "string", "minLength": 1 },
    "userId": { "type": "string", "minLength": 1 },
    "latitude": { "type": "number", "minimum": -90, "maximum": 90 },
    "longitude": { "type": "number", "minimum": -180, "maximum": 180 },
    "batteryPct": { "type": ["integer", "null"], "minimum": 0, "maximum": 100 },
    "odometerKm": { "type": ["number", "null"], "minimum": 0 }
  },
  "additionalProperties": false
}
```

Notas:

- `additionalProperties: false` obliga a versionar si se añade un campo; para cambios
  aditivos se acepta `true` y se documenta.
- `batteryPct` y `odometerKm` admiten `null` de forma explícita.
- No hay `tripId` ni `zoneId` en el esquema de RAW.

---

## 6. AsyncAPI

`contracts/asyncapi/urbanmove.yaml` debe incluir:

- Canales por evento y por capa.
- Productores y consumidores.
- Esquema del sobre y del payload.
- Ejemplos válidos.
- Descripción de la DLQ.
- Notas de compatibilidad por `eventVersion`.

---

## 7. Checklist de un evento

- [ ] Nombre en PascalCase y en pasado.
- [ ] Capa identificada: RAW, TRUSTED, CURATED o dominio.
- [ ] Productor identificado.
- [ ] Consumidores identificados.
- [ ] `eventVersion` y `schema` presentes.
- [ ] JSON Schema versionado en `contracts/events/`.
- [ ] Ejemplo válido.
- [ ] **RAW no lleva `tripId`, `zoneId`, duración ni coste.**
- [ ] `batteryPct` y `odometerKm` son opcionales si aplica.
- [ ] Test de contrato en el productor.
- [ ] Test de idempotencia en el consumidor.
- [ ] Documentado en AsyncAPI.
- [ ] Incluido en el flujo de demo si aplica.
