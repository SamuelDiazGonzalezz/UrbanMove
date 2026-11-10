# Catálogo de eventos

> Los eventos son el mecanismo principal de desacoplo y la fuente de verdad del Data
> Lake. Este documento define el sobre común, el catálogo, las garantías de entrega y
> las reglas de idempotencia.

---

## 1. Sobre común

Todos los eventos usan el mismo sobre:

```json
{
  "eventId": "2f1c9e6a-7c2b-4f3e-9a1d-5b8c0d2e4f60",
  "eventType": "RideFinished",
  "eventVersion": 1,
  "occurredAt": "2026-11-10T18:30:00Z",
  "source": "rides-service",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "causationId": "0f9e8d7c-6b5a-4321-9876-543210fedcba",
  "producer": "rides-service",
  "payload": {
    "rideId": "ride-123",
    "vehicleId": "vehicle-456",
    "userId": "user-789",
    "originZoneId": "zone-centro",
    "destinationZoneId": "zone-playa",
    "startedAt": "2026-11-10T18:10:00Z",
    "finishedAt": "2026-11-10T18:30:00Z",
    "durationSeconds": 1200,
    "preliminaryCost": 3.5
  }
}
```

| Campo | Obligatorio | Descripción |
|---|---|---|
| `eventId` | Sí | UUID único del evento; base de la idempotencia |
| `eventType` | Sí | Nombre en PascalCase, por ejemplo `RideFinished` |
| `eventVersion` | Sí | Entero; se incrementa ante cambios incompatibles |
| `occurredAt` | Sí | ISO-8601 en UTC |
| `source` | Sí | Servicio que origina el evento |
| `correlationId` | Sí | Permite seguir un flujo completo entre servicios |
| `causationId` | No | `eventId` del evento que provocó este |
| `producer` | Sí | Nombre lógico del productor |
| `payload` | Sí | Datos específicos del evento |

---

## 2. Catálogo

### 2.1. Eventos obligatorios del enunciado

| Evento | Productor | Consumidores principales | Disparador |
|---|---|---|---|
| `UserRegistered` | `identity` | `user-profiles`, `history`, ingesta | Alta de usuario |
| `VehiclePurchased` | `inventory` | `availability`, `history`, ingesta | Compra/incorporación |
| `VehicleReserved` | `reservations` | `availability`, `rides`, `history`, ingesta | Reserva creada |
| `ReservationCancelled` | `reservations` | `availability`, `history`, ingesta | Cancelación del usuario |
| `ReservationExpired` | `reservations` / Lambda | `availability`, `history`, ingesta | Timeout de reserva |
| `RideStarted` | `rides` | `availability`, `reservations`, `history`, ingesta | Inicio de viaje |
| `RideFinished` | `rides` | `availability`, `history`, ingesta, marts | Fin de viaje |
| `VehicleBatteryLow` | `telemetry` / Lambda | `maintenance`, `availability`, `history`, ingesta | Batería baja |
| `VehicleWithdrawnForMaintenance` | `maintenance` | `availability`, `history`, ingesta | Retirada del recurso |
| `VehicleReturnedToService` | `maintenance` | `availability`, `history`, ingesta | Vuelta al servicio |
| `DemandHotspotDetected` | Lambda de demanda | `relocation`, `history`, ingesta | Déficit detectado |
| `RelocationOrderCreated` | `relocation` | `history`, ingesta, marts | Orden creada |
| `RelocationOrderCompleted` | `relocation` | `history`, ingesta, marts | Orden completada |

### 2.2. Eventos adicionales propuestos

| Evento | Productor | Consumidores | Motivo |
|---|---|---|---|
| `UserProfileUpdated` | `user-profiles` | `history`, ingesta | Trazabilidad del perfil |
| `ZoneCreated` | `zones` | `availability`, `history`, ingesta | Catálogo de zonas |
| `VehicleTelemetryReceived` | `telemetry` | `maintenance`, ingesta | Batería, ubicación, km y usos |
| `IncidentReported` | `incidents` | `maintenance`, `history`, ingesta | Averías y vandalismo |
| `MaintenanceOrderCreated` | `maintenance` | `history`, ingesta, marts | Orden de mantenimiento |
| `MaintenanceOrderCompleted` | `maintenance` | `history`, ingesta, marts | Fin de la orden |
| `VehicleRetired` | `inventory` | `availability`, `history`, ingesta | Baja definitiva |

---

## 3. Reglas de versionado

- Los cambios **aditivos** (nuevos campos opcionales) no suben `eventVersion`.
- Los cambios **incompatibles** (quitar campos, cambiar tipos o semántica) suben
  `eventVersion` y requieren periodo de compatibilidad.
- Un consumidor debe ignorar campos desconocidos.
- Cada versión tiene su JSON Schema en `contracts/events/`.
- El catálogo se publica con AsyncAPI en `contracts/asyncapi/`.

---

## 4. Entrega, outbox e idempotencia

### 4.1. Garantía

Se asume **entrega al menos una vez** (at-least-once). Por tanto, todo consumidor debe
ser idempotente.

### 4.2. Outbox

En el servicio productor:

```text
BEGIN;
  -- cambio de negocio
  INSERT INTO reservation (...);
  -- evento en la misma transacción
  INSERT INTO outbox_event (event_id, event_type, payload, status, created_at)
  VALUES (...);
COMMIT;
```

Un relay (`@Scheduled` o proceso aparte) lee `outbox_event` pendiente, publica en
EventBridge y marca `SENT`. Si falla, reintenta.

La tabla es **append-only**: no se borran las filas publicadas, solo se marca `SENT`. Así
sirve a la vez de outbox y de event store para reconstruir el estado del servicio. La
copia que se guarda en S3 `raw/` es independiente y tiene fines analíticos; no sustituye
al outbox. Ver [`DATA-ARCHITECTURE.md`](DATA-ARCHITECTURE.md), sección 7.2.

### 4.3. Idempotencia

Cada consumidor mantiene:

```text
processed_event(event_id PRIMARY KEY, event_type, processed_at)
```

Antes de procesar:

1. Intentar insertar `event_id`.
2. Si ya existe, ignorar el mensaje.
3. Si no existe, procesar y confirmar.

La inserción y el procesamiento deben estar en la misma transacción cuando sea posible.

### 4.4. Reintentos y DLQ

- SQS reintenta con backoff.
- Tras N intentos, el mensaje va a la DLQ.
- Una alarma avisa cuando la DLQ tiene mensajes.
- La DLQ se revisa y se reprocesa manualmente o con una herramienta de replay.

### 4.5. Correlation ID

- Se genera en el edge o en el primer servicio que atiende la petición.
- Se propaga por HTTP (`X-Correlation-Id`) y por el sobre del evento.
- Se incluye en todos los logs.
- Permite reconstruir un flujo completo en Grafana/CloudWatch.

---

## 5. Ejemplo de JSON Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://urbanmove.local/schemas/RideFinished.v1.json",
  "title": "RideFinished",
  "type": "object",
  "required": ["rideId", "vehicleId", "userId", "finishedAt"],
  "properties": {
    "rideId": { "type": "string" },
    "vehicleId": { "type": "string" },
    "userId": { "type": "string" },
    "originZoneId": { "type": "string" },
    "destinationZoneId": { "type": "string" },
    "startedAt": { "type": "string", "format": "date-time" },
    "finishedAt": { "type": "string", "format": "date-time" },
    "durationSeconds": { "type": "integer", "minimum": 0 },
    "preliminaryCost": { "type": "number", "minimum": 0 }
  },
  "additionalProperties": false
}
```

---

## 6. AsyncAPI

El catálogo se documentará con AsyncAPI 3 en `contracts/asyncapi/urbanmove.yaml`.
Debe incluir:

- Canales por evento.
- Productores y consumidores.
- Esquema del sobre y del payload.
- Ejemplos.
- Descripción de la DLQ.

---

## 7. Checklist de un evento

- [ ] Nombre en PascalCase y en pasado.
- [ ] Productor identificado.
- [ ] Consumidores identificados.
- [ ] JSON Schema versionado.
- [ ] Ejemplo válido.
- [ ] Test de contrato en el productor.
- [ ] Test de idempotencia en el consumidor.
- [ ] Documentado en AsyncAPI.
- [ ] Incluido en el flujo de demo si aplica.
