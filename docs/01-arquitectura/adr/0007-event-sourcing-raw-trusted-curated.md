# 0007. Event sourcing inmutable con capas RAW, TRUSTED y CURATED

- **Estado:** Aceptada
- **Fecha:** 2026-11-11
- **Decisores:** Equipo UrbanMove

## Contexto

El diseño anterior trataba `outbox_event` como *event store* y permitía `UPDATE` sobre
las tablas de estado: la reserva pasaba de `ACTIVE` a `CONFIRMED`, la disponibilidad se
sobrescribía y los marts se incrementaban con `valor = valor + 1`. Eso funciona, pero
tiene tres problemas para este proyecto:

1. **No se puede explicar el pasado.** Si un vehículo aparece en `IN_USE` sin viaje, no
   hay forma de saber qué evento lo dejó así.
2. **No se puede reconstruir.** Una tabla sobrescrita no se puede regenerar a partir de
   los eventos: la información intermedia ya no existe.
3. **Mezcla dos granos.** Un `RideFinished` con `tripId`, zonas, duración y coste no es
   un hecho físico: es una interpretación. Si el cálculo de la zona estaba mal, el
   error queda enterrado en el "hecho".

El equipo quiere que **todo sea inmutable mediante eventos**: ninguna tabla de estado
mutable como fuente de verdad.

## Decisión

Se adopta **event sourcing** como modelo de persistencia en los doce servicios de
negocio, con tres capas de datos bien separadas.

### 1. El evento es la verdad

Cada servicio tiene un `event_store` **append-only**:

- Sin `UPDATE` ni `DELETE` de negocio.
- Corrección = emitir un evento nuevo, nunca reescribir el anterior.
- Orden garantizado por `global_position` y versión por agregado con
  `UNIQUE (aggregate_id, aggregate_version)`.

### 2. El estado actual es una proyección

Las tablas de estado son **proyecciones append-only**:

- Cada cambio añade una fila con una `version` nueva.
- La vista `<tabla>_current` devuelve la última versión con `DISTINCT ON`.
- Se pueden truncar y reconstruir con un *replay* del `event_store`.
- Los *snapshots* son opcionales, desechables y **nunca** fuente de verdad.

### 3. La publicación también es inmutable

- `outbox_event` encola el evento en la misma transacción que el cambio de agregado.
- `event_publication` registra cada publicación; **no** se marca `SENT` con un `UPDATE`.
- Idempotencia del consumidor con `processed_event`.

### 4. Tres capas, tres granos

| Capa | Qué es | Nombres |
|---|---|---|
| **RAW** | Hecho físico, tal como ocurrió | `VehiclePickedUp`, `VehicleDroppedOff` |
| **TRUSTED** | Hecho correlacionado y validado | `trip`, evento `TripRecorded` |
| **CURATED** | Agregado para decidir | `mart_viajes`, `mart_demanda`… |

Reglas de grano:

- **RAW no tiene `tripId`.** El viaje no existe todavía; lo crea TRUSTED.
- **RAW no tiene `zoneId`.** La zona es una derivación geográfica, no un hecho del
  dispositivo.
- **RAW no tiene duración ni coste.** Son cálculos posteriores.
- **`batteryPct` y `odometerKm` son opcionales** en RAW: no todos los vehículos son
  eléctricos ni tienen odómetro.
- **Cada evento lleva `eventVersion` y `schema`** (URI del JSON Schema).
- **`trip` es TRUSTED**: un registro por viaje, generado al recibir el dropoff.
- **`duration` y `distance` a nivel de viaje son TRUSTED; sus agregados son CURATED.**

### 5. El bus es para operar, S3 es para analizar

- `availability`, `rides` y `history` consumen eventos del **bus**.
- `analytics-ingest` copia el bus a `s3://…/raw/`.
- `mart-builder` lee `trusted/` y escribe marts.
- **Ningún servicio operacional lee `s3://…/raw/` en tiempo de ejecución.**

### 6. La excepción: Keycloak

Keycloak es un sistema externo gestionado. No se event-source: se integra por API y sus
tablas no se tocan. Es la única pieza que queda fuera del modelo.

## Alternativas consideradas

### A. Estado mutable con auditoría (`created_at`/`updated_at`)

- **Ventaja:** sencillo, es lo que hace todo el mundo.
- **Inconveniente:** no permite reconstruir estados intermedios ni explicar por qué un
  vehículo está como está; el "historial" es una copia parcial, no la verdad.
- **Descartada:** no cumple el objetivo de inmutabilidad total.

### B. Event sourcing solo en `rides` y `history`

- **Ventaja:** menos trabajo, solo en el núcleo del negocio.
- **Inconveniente:** deja dos modelos mentales y dos formas de auditar. La doble reserva
  seguiría dependiendo de un `UPDATE` condicional.
- **Descartada:** el equipo prefiere una sola regla para todo.

### C. Guardar solo el viaje completo (`TripCompleted`) y no los hechos físicos

- **Ventaja:** un evento por viaje, menos volumen.
- **Inconveniente:** se pierde el pickup cuando no hay dropoff; no se puede detectar un
  viaje huérfano; el `tripId` nace en el backend y deja de ser un hecho.
- **Descartada:** se descarta también el nombre `TripCompleted`; el evento es
  `TripRecorded` y la entidad es `trip`.

## Consecuencias

### A favor

- Se puede reconstruir cualquier proyección y cualquier mart desde el `event_store`.
- Se puede explicar el estado actual: cada fila de `*_current` apunta a su evento.
- La doble reserva se resuelve con versión esperada y stream por vehículo, no con un
  `UPDATE` condicional.
- RAW es auditable y no contiene datos derivados que puedan estar mal.
- El Data Lake se regenera: `curated` desde `trusted`, `trusted` desde `raw`.

### Coste

- Más filas: cada cambio es una versión nueva.
- Las consultas de estado pasan por vistas; hay que indexar `(id, version)`.
- El equipo debe aprender el patrón: agregado, comando, evento, proyección.
- Hay que vigilar el crecimiento del `event_store`; se archiva en S3, no se borra.
- Los marts son append-only por `build_id`: cada build añade filas y la vista
  `*_current` selecciona el último.

### Impacto documental

- [EVENT-SOURCING.md](../EVENT-SOURCING.md) es el documento de referencia.
- [EVENTS.md](../EVENTS.md) pasa a describir RAW, TRUSTED y CURATED.
- [DATA-DICTIONARY.md](../DATA-DICTIONARY.md) marca qué tablas son proyección.
- [DATA-MODEL.md](../DATA-MODEL.md) fija el grano de cada capa.
- [SERVICE-CLASSIFICATION.md](../SERVICE-CLASSIFICATION.md) clasifica OLTP y OLAP.
- [DATA-ARCHITECTURE.md](../DATA-ARCHITECTURE.md) se ajusta: el event store ya no es
  `outbox_event`.

## Mapeo con la rúbrica

| Evento de la rúbrica | Evento del proyecto | Capa |
|---|---|---|
| `RideStarted` | `VehiclePickedUp` | RAW |
| `RideFinished` | `TripRecorded` | TRUSTED |

El catálogo obligatorio se cumple sin publicar un `TripStarted` artificial.

## Referencias

- [EVENT-SOURCING.md](../EVENT-SOURCING.md)
- [EVENTS.md](../EVENTS.md)
- [DATA-MODEL.md](../DATA-MODEL.md)
- [DATA-DICTIONARY.md](../DATA-DICTIONARY.md)
- [ADR 0003](0003-kappa-medallion.md): Kappa + Medallón.
- Enunciado original: [urbanmove.pdf](../../04-enunciado/urbanmove.pdf).
