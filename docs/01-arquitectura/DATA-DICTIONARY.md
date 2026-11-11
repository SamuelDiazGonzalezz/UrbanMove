# Diccionario de datos

> **Estado:** documento vivo. Se actualiza con cada migración Flyway.
> **Última actualización:** 2026-11-11.
> **Ámbito:** atributos de todas las tablas del modelo, con tipo, nulabilidad y
> restricciones.
> **Documentos relacionados:** [DATA-MODEL.md](DATA-MODEL.md) (relaciones),
> [EVENT-SOURCING.md](EVENT-SOURCING.md) (event store y proyecciones),
> [DATA-ARCHITECTURE.md](DATA-ARCHITECTURE.md) (S3 y marts) y
> [SERVICE-CLASSIFICATION.md](SERVICE-CLASSIFICATION.md) (OLTP/OLAP).

---

## 0. Convenciones

| Convención | Valor |
|---|---|
| Base de datos | Una por servicio; nombre `<servicio>_db` |
| Tablas | Singular, `snake_case` |
| Claves primarias | `UUID`, salvo claves naturales (`model_code`, `code`) |
| Timestamps | `timestamptz`, siempre UTC |
| Dinero | `numeric(12,2)` |
| Estados | `varchar` + `CHECK`, no enums de PostgreSQL |
| Proyecciones | Append-only; la vista `<tabla>_current` devuelve la última versión |
| Eventos | `event_store` es la verdad; las tablas de estado son derivadas |

> **Importante:** las tablas marcadas como **proyección** no se actualizan ni se borran
> fila a fila. Cada cambio añade una versión nueva. La vista `*_current` selecciona la
> última. Se pueden truncar y reconstruir desde `event_store`.

---

## 1. Tablas comunes a todos los servicios

Estas tablas existen en cada base de datos de servicio, no en `analytics_db` ni en
`keycloak_db`.

### 1.1. `event_store`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `global_position` | `bigserial` | No | PK | Orden global de replay |
| `event_id` | `uuid` | No | Unique | Identificador del evento |
| `aggregate_type` | `varchar(64)` | No | — | Tipo de agregado (`Vehicle`, `Reservation`…) |
| `aggregate_id` | `uuid` | No | — | Identificador del agregado |
| `aggregate_version` | `integer` | No | — | Versión del agregado; base de la concurrencia |
| `event_type` | `varchar(64)` | No | — | `VehiclePickedUp`, `TripRecorded`… |
| `event_version` | `integer` | No | `1` | Versión del esquema del payload |
| `schema_uri` | `varchar(255)` | Sí | — | URI del JSON Schema |
| `occurred_at` | `timestamptz` | No | — | Cuándo ocurrió el hecho |
| `recorded_at` | `timestamptz` | No | `now()` | Cuándo se guardó |
| `correlation_id` | `uuid` | Sí | — | Traza del flujo completo |
| `causation_id` | `uuid` | Sí | — | Evento que provocó este |
| `actor_id` | `uuid` | Sí | — | Usuario o sistema que originó la acción |
| `payload` | `jsonb` | No | — | Datos del evento |
| `metadata` | `jsonb` | No | `'{}'` | Información de infraestructura |

Restricciones:

- `UNIQUE (aggregate_id, aggregate_version)`.
- Índices: `(aggregate_id, aggregate_version)`, `(event_type, occurred_at)`,
  `(correlation_id)`.
- Sin `UPDATE` ni `DELETE` sobre eventos de negocio.

### 1.2. `outbox_event`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `event_id` | `uuid` | No | PK, FK → `event_store` | Evento a publicar |
| `destination` | `varchar(64)` | No | — | Bus o cola destino |
| `created_at` | `timestamptz` | No | `now()` | Cuándo se encoló |

### 1.3. `event_publication`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `publication_id` | `uuid` | No | PK | Identificador de la publicación |
| `event_id` | `uuid` | No | — | Evento publicado |
| `destination` | `varchar(64)` | No | — | Destino |
| `published_at` | `timestamptz` | No | `now()` | Cuándo se publicó |

Restricción: `UNIQUE (event_id, destination)`.

> El relay publica eventos de `outbox_event` que no tienen fila en
> `event_publication`. No se actualiza nada: se añade una publicación.

### 1.4. `processed_event`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `event_id` | `uuid` | No | PK | Evento consumido |
| `consumer` | `varchar(64)` | No | — | Nombre del consumidor |
| `processed_at` | `timestamptz` | No | `now()` | Cuándo se procesó |

### 1.5. `aggregate_snapshot` (opcional)

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `snapshot_id` | `uuid` | No | PK | Identificador |
| `aggregate_type` | `varchar(64)` | No | — | Tipo de agregado |
| `aggregate_id` | `uuid` | No | — | Agregado |
| `aggregate_version` | `integer` | No | — | Versión que representa |
| `state` | `jsonb` | No | — | Estado serializado |
| `created_at` | `timestamptz` | No | `now()` | Cuándo se creó |

Restricción: `UNIQUE (aggregate_id, aggregate_version)`.

---

## 2. `zones_db`

### 2.1. `zone` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `zone_id` | `uuid` | No | PK | Identificador |
| `version` | `integer` | No | — | Versión de la proyección |
| `code` | `varchar(32)` | No | Unique | Código corto (`centro`, `playa`) |
| `name` | `varchar(120)` | No | — | Nombre visible |
| `center_lat` | `numeric(9,6)` | Sí | — | Latitud del centro |
| `center_lng` | `numeric(9,6)` | Sí | — | Longitud del centro |
| `radius_m` | `integer` | Sí | — | Radio operativo |
| `capacity` | `integer` | Sí | — | Capacidad orientativa |
| `status` | `varchar(16)` | No | `'ACTIVE'` | `ACTIVE` o `INACTIVE` |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia de esta versión |

### 2.2. `zone_restriction` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `restriction_id` | `uuid` | No | PK | Identificador |
| `zone_id` | `uuid` | No | — | Zona afectada |
| `version` | `integer` | No | — | Versión |
| `type` | `varchar(32)` | No | — | `NO_PARKING`, `MAX_VEHICLES`, `SPEED_LIMIT` |
| `value` | `varchar(64)` | Sí | — | Valor de la restricción |
| `valid_from` | `timestamptz` | No | `now()` | Inicio |
| `valid_to` | `timestamptz` | Sí | — | Fin; `NULL` si sigue vigente |

Vistas: `zone_current`, `zone_restriction_current`.

---

## 3. `identity_db`

### 3.1. `user_account` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `user_id` | `uuid` | No | PK | Identificador de negocio |
| `version` | `integer` | No | — | Versión |
| `subject_id` | `varchar(64)` | No | Unique | `sub` de Keycloak |
| `email` | `varchar(254)` | No | Unique | Correo; dato personal |
| `status` | `varchar(16)` | No | `'ACTIVE'` | `ACTIVE` o `DISABLED` |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia |

### 3.2. `role_assignment` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `assignment_id` | `uuid` | No | PK | Identificador |
| `user_id` | `uuid` | No | FK → `user_account` | Usuario |
| `version` | `integer` | No | — | Versión |
| `role` | `varchar(32)` | No | — | `USER`, `OPERATIONS`, `ADMIN` |
| `assigned_at` | `timestamptz` | No | `now()` | Alta |
| `revoked_at` | `timestamptz` | Sí | — | Baja; `NULL` si vigente |

Vistas: `user_account_current`, `role_assignment_current`.

---

## 4. `user_profiles_db`

### 4.1. `user_profile` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `user_id` | `uuid` | No | PK | Mismo ID que `identity` |
| `version` | `integer` | No | — | Versión |
| `display_name` | `varchar(120)` | Sí | — | Nombre visible |
| `phone` | `varchar(32)` | Sí | — | Teléfono; dato personal |
| `locale` | `varchar(10)` | No | `'es-ES'` | Idioma |
| `preferences` | `jsonb` | No | `'{}'` | Preferencias libres |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia |

Vista: `user_profile_current`.

---

## 5. `inventory_db`

### 5.1. `vehicle_model`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `model_code` | `varchar(32)` | No | PK | Código del modelo |
| `name` | `varchar(120)` | No | — | Nombre comercial |
| `type` | `varchar(16)` | No | — | `BIKE`, `E_BIKE`, `SCOOTER` |
| `battery_capacity_wh` | `integer` | Sí | — | Solo eléctricos |
| `max_speed_kmh` | `numeric(5,2)` | Sí | — | Velocidad máxima |

### 5.2. `vehicle_asset` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `vehicle_id` | `uuid` | No | PK | Identificador |
| `version` | `integer` | No | — | Versión |
| `license_plate` | `varchar(32)` | No | Unique | Matrícula o código visible |
| `model_code` | `varchar(32)` | No | FK → `vehicle_model` | Modelo |
| `purchase_date` | `date` | Sí | — | Fecha de compra |
| `status` | `varchar(16)` | No | `'ACTIVE'` | `ACTIVE` o `RETIRED` |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia |

### 5.3. `acquisition`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `acquisition_id` | `uuid` | No | PK | Identificador |
| `vehicle_id` | `uuid` | No | FK → `vehicle_asset` | Vehículo |
| `acquired_at` | `timestamptz` | No | — | Fecha de adquisición |
| `supplier` | `varchar(120)` | Sí | — | Proveedor |
| `unit_cost` | `numeric(12,2)` | Sí | — | Coste unitario |
| `invoice_ref` | `varchar(64)` | Sí | — | Referencia de factura |

Vistas: `vehicle_asset_current`.

---

## 6. `availability_db`

### 6.1. `vehicle_availability` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `vehicle_id` | `uuid` | No | PK | Vehículo |
| `version` | `integer` | No | — | Versión |
| `zone_id` | `uuid` | Sí | — | Zona actual |
| `state` | `varchar(24)` | No | — | Estado operativo |
| `reservation_id` | `uuid` | Sí | — | Reserva activa |
| `trip_id` | `uuid` | Sí | — | Viaje activo |
| `battery_pct` | `integer` | Sí | — | Batería; `NULL` si no aplica |
| `source_event_id` | `uuid` | Sí | — | Evento que originó la versión |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia |

Estados: `AVAILABLE`, `RESERVED`, `IN_USE`, `LOW_BATTERY`, `WITHDRAWN`, `CHARGING`,
`REPAIRING`, `READY_FOR_SERVICE`, `RETIRED`.

### 6.2. `availability_transition`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `transition_id` | `uuid` | No | PK | Identificador |
| `vehicle_id` | `uuid` | No | — | Vehículo |
| `from_state` | `varchar(24)` | Sí | — | Estado anterior |
| `to_state` | `varchar(24)` | No | — | Estado nuevo |
| `reason` | `varchar(64)` | Sí | — | Motivo |
| `event_id` | `uuid` | No | Unique | Evento que la provocó |
| `occurred_at` | `timestamptz` | No | — | Cuándo |

Vista: `vehicle_availability_current`.

---

## 7. `reservations_db`

### 7.1. `reservation` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `reservation_id` | `uuid` | No | PK | Identificador |
| `version` | `integer` | No | — | Versión |
| `user_id` | `uuid` | No | — | Usuario |
| `vehicle_id` | `uuid` | No | — | Vehículo |
| `origin_zone_id` | `uuid` | Sí | — | Zona de recogida |
| `status` | `varchar(16)` | No | `'ACTIVE'` | `ACTIVE`, `CONFIRMED`, `CANCELLED`, `EXPIRED`, `COMPLETED` |
| `created_at` | `timestamptz` | No | `now()` | Creación |
| `expires_at` | `timestamptz` | No | — | Caducidad |
| `confirmed_at` | `timestamptz` | Sí | — | Confirmación |
| `closed_at` | `timestamptz` | Sí | — | Cierre |
| `cancellation_reason` | `varchar(64)` | Sí | — | Motivo |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia |

### 7.2. `reservation_attempt`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `attempt_id` | `uuid` | No | PK | Identificador |
| `reservation_id` | `uuid` | Sí | FK → `reservation` | Reserva creada, si hubo |
| `user_id` | `uuid` | No | — | Usuario |
| `vehicle_id` | `uuid` | Sí | — | Vehículo intentado |
| `zone_id` | `uuid` | Sí | — | Zona |
| `attempted_at` | `timestamptz` | No | `now()` | Cuándo |
| `result` | `varchar(24)` | No | — | `SUCCESS`, `NO_AVAILABILITY`, `CONFLICT`, `INVALID` |
| `rejection_reason` | `varchar(64)` | Sí | — | Motivo del fallo |

### 7.3. `reservation_slot`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `slot_id` | `uuid` | No | PK | Identificador |
| `vehicle_id` | `uuid` | No | — | Vehículo |
| `reservation_id` | `uuid` | No | — | Reserva que ocupa el slot |
| `held_at` | `timestamptz` | No | `now()` | Cuándo se tomó |
| `released_at` | `timestamptz` | Sí | — | Cuándo se liberó; `NULL` si activo |
| `release_reason` | `varchar(32)` | Sí | — | `CANCELLED`, `EXPIRED`, `VEHICLE_PICKED_UP` |

Vistas: `reservation_current`, `reservation_slot_current`.

---

## 8. `rides_db`

### 8.1. `open_trip` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `open_trip_id` | `uuid` | No | PK | Identificador interno |
| `vehicle_id` | `uuid` | No | — | Vehículo |
| `user_id` | `uuid` | No | — | Usuario |
| `pickup_event_id` | `uuid` | No | Unique | Evento `VehiclePickedUp` |
| `started_at` | `timestamptz` | No | — | Inicio |
| `pickup_zone_id` | `uuid` | Sí | — | Zona derivada del pickup |
| `status` | `varchar(16)` | No | `'OPEN'` | `OPEN`, `CLOSED`, `ORPHAN` |
| `closed_at` | `timestamptz` | Sí | — | Cierre |

Vista: `open_trip_current` (solo `status = 'OPEN'`).

### 8.2. `trip` — TRUSTED

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `trip_id` | `uuid` | No | PK | Identificador generado en TRUSTED |
| `version` | `integer` | No | — | Versión |
| `reservation_id` | `uuid` | Sí | — | Reserva asociada |
| `vehicle_id` | `uuid` | No | — | Vehículo |
| `user_id` | `uuid` | No | — | Usuario |
| `origin_zone_id` | `uuid` | Sí | — | Zona de origen; `NULL` en `ORPHAN` |
| `destination_zone_id` | `uuid` | Sí | — | Zona de destino; `NULL` si no se pudo derivar |
| `started_at` | `timestamptz` | Sí | — | Pickup; `NULL` en `ORPHAN` |
| `finished_at` | `timestamptz` | No | — | Dropoff |
| `duration_seconds` | `integer` | Sí | — | Derivado; `NULL` si falta `started_at` |
| `distance_meters` | `integer` | Sí | — | Derivado; `NULL` si no hay datos |
| `battery_drop_pct` | `integer` | Sí | — | Derivado; solo eléctricos |
| `preliminary_cost` | `numeric(12,2)` | Sí | — | Coste preliminar |
| `status` | `varchar(16)` | No | `'COMPLETED'` | `COMPLETED`, `SUSPECT`, `ORPHAN` |
| `recorded_at` | `timestamptz` | No | `now()` | Cuándo se registró |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia |

Restricciones de `trip`:

- `CHECK (status <> 'COMPLETED' OR (origin_zone_id IS NOT NULL AND destination_zone_id IS NOT NULL AND started_at IS NOT NULL AND duration_seconds IS NOT NULL))`.
- `distance_meters` y `battery_drop_pct` pueden ser `NULL` aunque el viaje esté completo.
- `preliminary_cost` puede ser `NULL` si aún no hay tarifa.

Vista: `trip_current`.

---

## 9. `history_db`

### 9.1. `history_entry`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `entry_id` | `uuid` | No | PK | Identificador |
| `event_id` | `uuid` | No | Unique | Evento original |
| `event_type` | `varchar(64)` | No | — | Tipo |
| `event_version` | `integer` | No | — | Versión del esquema |
| `aggregate_type` | `varchar(64)` | No | — | Agregado |
| `aggregate_id` | `uuid` | No | — | ID del agregado |
| `user_id` | `uuid` | Sí | — | Usuario implicado |
| `vehicle_id` | `uuid` | Sí | — | Vehículo implicado |
| `occurred_at` | `timestamptz` | No | — | Cuándo ocurrió |
| `correlation_id` | `uuid` | Sí | — | Traza |
| `payload` | `jsonb` | No | — | Evento completo |
| `ingested_at` | `timestamptz` | No | `now()` | Cuándo se guardó |

Índices: `(aggregate_type, aggregate_id, occurred_at)`, `(user_id, occurred_at)`,
`(vehicle_id, occurred_at)`.

---

## 10. `maintenance_db`

### 10.1. `maintenance_order` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `order_id` | `uuid` | No | PK | Identificador |
| `version` | `integer` | No | — | Versión |
| `vehicle_id` | `uuid` | No | — | Vehículo |
| `incident_id` | `uuid` | Sí | — | Incidencia que la originó |
| `type` | `varchar(16)` | No | — | `PREVENTIVE`, `CORRECTIVE`, `CHARGING`, `INSPECTION` |
| `reason` | `varchar(255)` | Sí | — | Motivo |
| `priority` | `varchar(8)` | No | `'MEDIUM'` | `LOW`, `MEDIUM`, `HIGH` |
| `status` | `varchar(16)` | No | `'OPEN'` | `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `opened_at` | `timestamptz` | No | `now()` | Apertura |
| `started_at` | `timestamptz` | Sí | — | Inicio |
| `closed_at` | `timestamptz` | Sí | — | Cierre |
| `assigned_to` | `varchar(64)` | Sí | — | Técnico asignado |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia |

### 10.2. `maintenance_task`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `task_id` | `uuid` | No | PK | Identificador |
| `order_id` | `uuid` | No | FK → `maintenance_order` | Orden |
| `version` | `integer` | No | — | Versión |
| `description` | `varchar(255)` | No | — | Tarea |
| `status` | `varchar(16)` | No | `'PENDING'` | `PENDING`, `IN_PROGRESS`, `DONE` |
| `performed_at` | `timestamptz` | Sí | — | Cuándo se hizo |
| `technician` | `varchar(64)` | Sí | — | Técnico |
| `cost` | `numeric(12,2)` | Sí | — | Coste |

Vistas: `maintenance_order_current`, `maintenance_task_current`.

---

## 11. `telemetry_db`

### 11.1. `telemetry_reading`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `reading_id` | `uuid` | No | PK | Identificador |
| `event_id` | `uuid` | No | Unique | Evento original |
| `vehicle_id` | `uuid` | No | — | Vehículo |
| `battery_pct` | `integer` | Sí | — | Batería; `NULL` si no aplica |
| `latitude` | `numeric(9,6)` | Sí | — | Latitud |
| `longitude` | `numeric(9,6)` | Sí | — | Longitud |
| `odometer_km` | `numeric(10,1)` | Sí | — | Odómetro; `NULL` si no tiene |
| `recorded_at` | `timestamptz` | No | — | Cuándo lo midió el vehículo |
| `received_at` | `timestamptz` | No | `now()` | Cuándo llegó |

Índices: `(vehicle_id, recorded_at DESC)`, BRIN en `recorded_at`.

### 11.2. `telemetry_latest` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `vehicle_id` | `uuid` | No | PK | Vehículo |
| `version` | `integer` | No | — | Versión |
| `battery_pct` | `integer` | Sí | — | Última batería |
| `latitude` | `numeric(9,6)` | Sí | — | Última latitud |
| `longitude` | `numeric(9,6)` | Sí | — | Última longitud |
| `odometer_km` | `numeric(10,1)` | Sí | — | Último odómetro |
| `recorded_at` | `timestamptz` | No | — | Última medición |

Vista: `telemetry_latest_current`.

---

## 12. `incidents_db`

### 12.1. `incident` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `incident_id` | `uuid` | No | PK | Identificador |
| `version` | `integer` | No | — | Versión |
| `vehicle_id` | `uuid` | No | — | Vehículo |
| `zone_id` | `uuid` | Sí | — | Zona |
| `reported_by_user_id` | `uuid` | Sí | — | Quien reporta |
| `type` | `varchar(16)` | No | — | `BREAKDOWN`, `VANDALISM`, `ACCIDENT`, `OTHER` |
| `description` | `varchar(500)` | Sí | — | Descripción |
| `severity` | `varchar(8)` | No | `'MEDIUM'` | `LOW`, `MEDIUM`, `HIGH` |
| `status` | `varchar(16)` | No | `'OPEN'` | `OPEN`, `IN_REVIEW`, `RESOLVED`, `DISCARDED` |
| `reported_at` | `timestamptz` | No | `now()` | Reporte |
| `resolved_at` | `timestamptz` | Sí | — | Resolución |
| `maintenance_order_id` | `uuid` | Sí | — | Orden asociada |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia |

Vista: `incident_current`.

---

## 13. `relocation_db`

### 13.1. `relocation_order` — proyección

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `order_id` | `uuid` | No | PK | Identificador |
| `version` | `integer` | No | — | Versión |
| `from_zone_id` | `uuid` | No | — | Zona origen |
| `to_zone_id` | `uuid` | No | — | Zona destino |
| `reason` | `varchar(24)` | No | — | `DEMAND_DEFICIT`, `REBALANCE`, `MANUAL` |
| `status` | `varchar(16)` | No | `'CREATED'` | `CREATED`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `created_at` | `timestamptz` | No | `now()` | Creación |
| `completed_at` | `timestamptz` | Sí | — | Cierre |
| `assigned_to` | `varchar(64)` | Sí | — | Operador |
| `valid_from` | `timestamptz` | No | `now()` | Inicio de vigencia |

### 13.2. `relocation_item`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `item_id` | `uuid` | No | PK | Identificador |
| `order_id` | `uuid` | No | FK → `relocation_order` | Orden |
| `vehicle_id` | `uuid` | No | — | Vehículo |
| `status` | `varchar(16)` | No | `'PENDING'` | `PENDING`, `MOVED`, `FAILED` |
| `moved_at` | `timestamptz` | Sí | — | Cuándo se movió |

Vistas: `relocation_order_current`, `relocation_item_current`.

---

## 14. `analytics_db`

Todas las tablas de marts son **append-only por build**. Cada fila lleva `build_id`; la
vista `*_current` lee el último build correcto. No hay `UPDATE`.

### 14.1. `mart_build`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `build_id` | `uuid` | No | PK | Identificador del build |
| `mart_name` | `varchar(64)` | No | — | Mart construido |
| `window_start` | `timestamptz` | No | — | Inicio de ventana |
| `window_end` | `timestamptz` | No | — | Fin de ventana |
| `built_at` | `timestamptz` | No | `now()` | Cuándo se construyó |
| `status` | `varchar(16)` | No | `'SUCCESS'` | `SUCCESS` o `FAILED` |
| `row_count` | `bigint` | Sí | — | Filas generadas |

### 14.2. `mart_viajes`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `build_id` | `uuid` | No | PK, FK → `mart_build` | Build |
| `date_key` | `date` | No | PK | Día |
| `hour` | `smallint` | No | PK | Hora |
| `origin_zone_id` | `uuid` | No | PK | Zona origen |
| `destination_zone_id` | `uuid` | No | PK | Zona destino |
| `vehicle_type` | `varchar(16)` | No | PK | Tipo |
| `trips` | `bigint` | No | — | Viajes |
| `avg_duration_seconds` | `numeric(10,2)` | No | — | Duración media |
| `total_distance_meters` | `bigint` | Sí | — | Distancia total |
| `revenue` | `numeric(12,2)` | No | — | Ingresos |

### 14.3. `mart_reservas`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `build_id` | `uuid` | No | PK | Build |
| `date_key` | `date` | No | PK | Día |
| `hour` | `smallint` | No | PK | Hora |
| `zone_id` | `uuid` | No | PK | Zona |
| `vehicle_type` | `varchar(16)` | No | PK | Tipo |
| `reservations_created` | `bigint` | No | — | Creadas |
| `confirmed` | `bigint` | No | — | Confirmadas |
| `cancelled` | `bigint` | No | — | Canceladas |
| `expired` | `bigint` | No | — | Expiradas |
| `conversion_rate` | `numeric(5,4)` | Sí | — | Confirmadas / creadas |

### 14.4. `mart_flota`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `build_id` | `uuid` | No | PK | Build |
| `date_key` | `date` | No | PK | Día |
| `hour` | `smallint` | No | PK | Hora |
| `zone_id` | `uuid` | No | PK | Zona |
| `vehicle_type` | `varchar(16)` | No | PK | Tipo |
| `available` | `bigint` | No | — | Disponibles |
| `reserved` | `bigint` | No | — | Reservadas |
| `in_use` | `bigint` | No | — | En uso |
| `low_battery` | `bigint` | No | — | Batería baja |
| `withdrawn` | `bigint` | No | — | Retiradas |
| `total` | `bigint` | No | — | Total |

### 14.5. `mart_mantenimiento`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `build_id` | `uuid` | No | PK | Build |
| `date_key` | `date` | No | PK | Día |
| `vehicle_type` | `varchar(16)` | No | PK | Tipo |
| `order_type` | `varchar(16)` | No | PK | Tipo de orden |
| `orders_opened` | `bigint` | No | — | Abiertas |
| `orders_closed` | `bigint` | No | — | Cerradas |
| `avg_duration_hours` | `numeric(10,2)` | Sí | — | Duración media |
| `total_cost` | `numeric(12,2)` | Sí | — | Coste total |

### 14.6. `mart_demanda`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `build_id` | `uuid` | No | PK | Build |
| `date_key` | `date` | No | PK | Día |
| `hour` | `smallint` | No | PK | Hora |
| `zone_id` | `uuid` | No | PK | Zona |
| `vehicle_type` | `varchar(16)` | No | PK | Tipo |
| `demand_attempts` | `bigint` | No | — | Intentos de reserva |
| `trips_started` | `bigint` | No | — | Viajes iniciados |
| `available_avg` | `numeric(10,2)` | No | — | Disponibilidad media |
| `deficit` | `integer` | No | `0` | Déficit calculado |
| `relocation_orders` | `bigint` | No | `0` | Órdenes generadas |

### 14.7. `mart_usuarios`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `build_id` | `uuid` | No | PK | Build |
| `date_key` | `date` | No | PK | Día |
| `new_users` | `bigint` | No | — | Altas |
| `active_users` | `bigint` | No | — | Usuarios activos |
| `trips` | `bigint` | No | — | Viajes |
| `trips_per_user` | `numeric(10,2)` | No | — | Viajes por usuario |
| `cancellations` | `bigint` | No | — | Cancelaciones |

### 14.8. `mart_operaciones`

| Columna | Tipo | Nulo | Clave / defecto | Descripción |
|---|---|---|---|---|
| `build_id` | `uuid` | No | PK | Build |
| `date_key` | `date` | No | PK | Día |
| `zone_id` | `uuid` | No | PK | Zona |
| `relocation_orders_created` | `bigint` | No | — | Creadas |
| `relocation_orders_completed` | `bigint` | No | — | Completadas |
| `avg_completion_minutes` | `numeric(10,2)` | Sí | — | Tiempo medio |

Vistas: `mart_viajes_current`, `mart_reservas_current`, `mart_flota_current`,
`mart_mantenimiento_current`, `mart_demanda_current`, `mart_usuarios_current`,
`mart_operaciones_current`.

---

## 15. `keycloak_db`

Base gestionada por Keycloak. **No se documentan ni se modifican sus tablas**: se
consultan por API. Contiene usuarios, credenciales, sesiones, roles y clientes.

---

## 16. S3: no son tablas

El Data Lake no se modela con tablas:

| Prefijo | Formato | Grano |
|---|---|---|
| `raw/` | JSON | Un evento por objeto |
| `trusted/` | Parquet | Un viaje por fila (`trips`) o entidad normalizada |
| `curated/` | Parquet | Agregados por dominio y ventana |

Rutas y reglas completas en [EVENT-SOURCING.md](EVENT-SOURCING.md), sección S3.

---

## 17. Mantenimiento de este documento

1. Cada migración Flyway que añada o cambie una columna actualiza este documento en el
   mismo PR.
2. Los cambios de tipo o nulabilidad se marcan en la descripción con la fecha.
3. Las tablas nuevas se añaden a la sección de su servicio.
4. Si una tabla cambia de dueño, se actualiza también [DATA-MODEL.md](DATA-MODEL.md).
5. Este documento no sustituye a las migraciones: **la verdad ejecutable es Flyway**.
