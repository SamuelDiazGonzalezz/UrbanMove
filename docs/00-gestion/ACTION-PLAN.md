# Plan de acción

> **Estado:** propuesta para revisión del equipo.
> **Última actualización:** 2026-11-11.
> **Ámbito:** qué se hace, en qué orden y en qué semana. Es la vista corta y operativa
> del [PLAN.md](PLAN.md); el detalle de cada tema está en su documento.
> **Plazo objetivo:** 8 semanas.
> **Referencias:** [PLAN.md](PLAN.md) (alcance y rúbrica),
> [SERVICE-ROADMAP.md](../01-arquitectura/SERVICE-ROADMAP.md) (dependencias),
> [KICKOFF.md](KICKOFF.md) (decisiones y reparto) y
> [enunciado original](../04-enunciado/urbanmove.pdf).

---

## 1. Regla de orden

Se construye siempre en este orden. No se empieza un servicio sin su contrato cerrado.

```text
1. Contrato OpenAPI + JSON Schema de eventos
2. Dominio y pruebas unitarias
3. Persistencia y pruebas con Testcontainers
4. Adaptadores REST y de mensajería
5. Event store, outbox y publicación
6. Dockerfile y servicio en Compose
7. Documentación, colección de API y evidencia
```

La razón está en [SERVICE-ROADMAP.md](../01-arquitectura/SERVICE-ROADMAP.md), sección 5:
permite trabajar en paralelo sin bloquearse y evita rehacer contratos.

---

## 2. Calendario en 8 semanas

| Semana | Objetivo | Qué se hace, en orden | Hecho cuando | Referencias |
|---|---|---|---|---|
| **1** | Cimientos | Compose con PostgreSQL, LocalStack, Keycloak, Grafana y Prometheus → POM padre → plantilla hexagonal → realm de Keycloak → bucket, bus, colas y DLQ en LocalStack → CI mínimo → esqueleto de `contracts/` → servicio `zones` | `docker compose up` levanta todo; `GET /zones` responde; CI verde | [LOCAL-DEV.md](../02-desarrollo/LOCAL-DEV.md), [CONTAINERS.md](../02-desarrollo/CONTAINERS.md), [CI-CD.md](../03-operacion/CI-CD.md) |
| **2** | Identidad y catálogo | `identity` con Keycloak → `user-profiles` → `inventory` → contratos de `availability` y `reservations` | Login con rol, usuarios y perfiles; vehículos dados de alta; contratos publicados | [ARCHITECTURE.md](../01-arquitectura/ARCHITECTURE.md), [EVENTS.md](../01-arquitectura/EVENTS.md) |
| **3** | Flujo de usuario | `availability` → `reservations` → `rides` → `history` → Lambda `analytics-ingest` → primer mart → 2-3 paneles | Registro → reserva → pickup → dropoff → historial → evento en S3 → panel | [SERVICE-ROADMAP.md](../01-arquitectura/SERVICE-ROADMAP.md), [EVENT-SOURCING.md](../01-arquitectura/EVENT-SOURCING.md) |
| **4** | Operaciones | `telemetry` → `incidents` → `maintenance` → Lambdas `reservation-expiration` y `maintenance-detection` → transiciones completas de estado | Batería baja → orden → retirada → reparación/recarga → vuelta al servicio | [PLAN.md](PLAN.md), sección 10, y [DATA-MODEL.md](../01-arquitectura/DATA-MODEL.md) |
| **5** | Analítica | `analytics-ingest` a `raw/` → `trusted/` → `curated/` → `mart-builder` → 4 marts obligatorios → dashboard con los paneles del enunciado | Los 4 marts se reconstruyen desde eventos y Grafana los muestra | [DATA-ARCHITECTURE.md](../01-arquitectura/DATA-ARCHITECTURE.md), [DATA-DICTIONARY.md](../01-arquitectura/DATA-DICTIONARY.md) |
| **6** | Recolocación y nube | `relocation` → Lambda `demand-relocation` → marts restantes → Terraform y despliegue AWS si hay cuenta | `mart_demanda` detecta déficit y se crea/completa una orden | [PLAN.md](PLAN.md), secciones 12 y 23 |
| **7** | Resiliencia y rendimiento | Pruebas de fallo → DLQ e idempotencia → carga con Locust → métricas, logs y trazas → análisis de cuellos de botella | Dos fallos demostrados con evidencia y métricas correlacionadas | [TESTING.md](../02-desarrollo/TESTING.md), [CI-CD.md](../03-operacion/CI-CD.md) |
| **8** | Cierre y defensa | Documentación final → PDF → ensayo de los 5 flujos → limpieza y coste → defensa | Demo de menos de 10 minutos que funciona desde cero | [PLAN.md](PLAN.md), sección 24 |

**Hitos de control:** al final de cada semana se levanta todo el sistema y se ejecuta el
flujo completo. Si no funciona, no se avanza: se corrige.

---

## 3. Orden exacto de servicios

1. `zones` — walking skeleton y catálogo.
2. `identity` — autenticación y roles.
3. `user-profiles` — perfil de usuario.
4. `inventory` — vehículos y adquisiciones.
5. `availability` — estado operativo.
6. `reservations` — reserva y doble reserva.
7. `rides` — pickup, dropoff y `TripRecorded`.
8. `history` — historial por eventos.
9. `telemetry` — batería, ubicación y odómetro.
10. `incidents` — averías y vandalismo.
11. `maintenance` — órdenes de taller.
12. `analytics` — marts y consultas.
13. `relocation` — órdenes de recolocación.

`zones`, `telemetry` e `incidents` no son obligatorios, pero completan los flujos. Si el
plazo aprieta, se recortan según [SERVICE-ROADMAP.md](../01-arquitectura/SERVICE-ROADMAP.md),
sección 8.

---

## 4. Contratos y documentación que no se pueden olvidar

Esta es la lista que cubre lo que la rúbrica llama "documentación arquitectónica" y
"APIs". Se revisa en cada entrega semanal.

| Entregable | Dónde vive | Mínimo |
|---|---|---|
| **OpenAPI 3** por servicio | `contracts/openapi/<servicio>.yaml` | Todos los endpoints de los 13 servicios, con ejemplos, errores RFC 7807 y versión `/api/v1` |
| **JSON Schema** por evento | `contracts/events/<Evento>.v1.json` | Cada evento publicado, con `eventVersion` y `schema` |
| **AsyncAPI 3** | `contracts/asyncapi/urbanmove.yaml` | Canales, productores, consumidores, payloads y DLQ |
| **C4** | [ARCHITECTURE.md](../01-arquitectura/ARCHITECTURE.md) | Contexto y contenedores |
| **Secuencias** | [ARCHITECTURE.md](../01-arquitectura/ARCHITECTURE.md) y [PLAN.md](PLAN.md) | Los 5 flujos de la demo |
| **Propiedad de datos** | [ARCHITECTURE.md](../01-arquitectura/ARCHITECTURE.md) | Una tabla por servicio; sin acceso cruzado |
| **Modelo y diccionario** | [DATA-MODEL.md](../01-arquitectura/DATA-MODEL.md), [DATA-DICTIONARY.md](../01-arquitectura/DATA-DICTIONARY.md) | Relaciones y atributos al día con Flyway |
| **ADRs** | `docs/01-arquitectura/adr/` | 0001-0007 como mínimo |
| **Colección de API** | `clients/` | Bruno o Postman con los flujos de usuario y operaciones |
| **README por servicio** | `services/<servicio>/README.md` | Arranque, variables y endpoints |

> **Regla:** un endpoint sin OpenAPI o un evento sin JSON Schema no se considera
> terminado, aunque el código funcione.

---

## 5. Entregables de la rúbrica

| Entregable | Mínimo | Dónde se demuestra |
|---|---|---|
| Microservicios atómicos | 10 obligatorios; 13 planificados | Catálogo de [ARCHITECTURE.md](../01-arquitectura/ARCHITECTURE.md) |
| Interfaces diferenciadas | Usuario, operaciones y dashboard | Swagger/Bruno + Grafana |
| Historial independiente | Servicio `history` propio | [SERVICE-CLASSIFICATION.md](../01-arquitectura/SERVICE-CLASSIFICATION.md) |
| Mensajería | Bus, colas y DLQ | EventBridge + SQS + DLQ |
| Lambdas | 3 obligatorias | `reservation-expiration`, `maintenance-detection`, `analytics-ingest`; `mart-builder` y `demand-relocation` como ampliación |
| Data Lake | `raw/`, `trusted/`, `curated/` | [EVENT-SOURCING.md](../01-arquitectura/EVENT-SOURCING.md) |
| Data marts | 4 mínimo; 7 objetivo | `analytics_db` y [DATA-DICTIONARY.md](../01-arquitectura/DATA-DICTIONARY.md) |
| Recolocación | Orden generada desde demanda | `mart_demanda` → `DemandHotspotDetected` → `RelocationOrderCreated` |
| Dashboard | Flota, reservas, viajes, mantenimiento, recolocación y salud | Grafana |
| Tolerancia a fallos | 4 mecanismos y 2 fallos probados | [PLAN.md](PLAN.md), sección 15 |
| Escalabilidad | Horizontal y 2 cuellos de botella | [PLAN.md](PLAN.md), sección 16 |
| DevOps | Tests, imágenes, análisis estático, migraciones y evidencias | [CI-CD.md](../03-operacion/CI-CD.md) |
| Observabilidad | Logs con `correlationId`, métricas y salud | Prometheus + Grafana + Actuator |
| PDF y defensa | Todos los integrantes | [PLAN.md](PLAN.md), secciones 24 y 25 |
| Coste | Recursos y limpieza | [PLAN.md](PLAN.md), sección 23 |

---

## 6. Qué se recorta si el plazo aprieta

En este orden, sin tocar el flujo de usuario:

1. `telemetry` e `incidents` se sustituyen por eventos simulados.
2. `relocation` se limita a la orden manual desde `mart_demanda`.
3. `analytics` se limita a 4 marts y 5 paneles.
4. **Nunca se recortan** `reservations`, `rides`, `history`, `maintenance`, `analytics`
   ni el flujo de usuario completo.

---

## 7. Rutina semanal

| Día | Qué se hace |
|---|---|
| Lunes | Revisar contratos y cerrar los de la semana |
| Miércoles | Integración parcial: levantar lo que haya y comprobar el flujo |
| Viernes | Integración completa, demo interna y actualización de checkboxes |

- Cada tarea que dure más de medio día o bloquee a otra persona se convierte en issue.
- Los PR son pequeños, con el CI verde y una revisión.
- El detalle está en [GITHUB-WORKFLOW.md](GITHUB-WORKFLOW.md).

---

## 8. Próximos 7 días

1. Validar las decisiones y este plan en la reunión de [KICKOFF.md](KICKOFF.md).
2. Crear `docker-compose.yml`, POM padre y plantilla hexagonal.
3. Configurar Keycloak y LocalStack (bucket, bus, colas y DLQ).
4. Crear el CI mínimo y el esqueleto de `contracts/`.
5. Escribir el OpenAPI de `zones` y el JSON Schema de `ZoneCreated`.
6. Implementar `zones` y validar el walking skeleton.
7. Actualizar [PLAN.md](PLAN.md) y este documento al cerrar la semana.

---

## 9. Enlaces rápidos

- [Plan maestro](PLAN.md)
- [Reunión de arranque](KICKOFF.md)
- [Flujo de trabajo en GitHub](GITHUB-WORKFLOW.md)
- [Roadmap de servicios](../01-arquitectura/SERVICE-ROADMAP.md)
- [Arquitectura](../01-arquitectura/ARCHITECTURE.md)
- [Event sourcing](../01-arquitectura/EVENT-SOURCING.md)
- [Modelo de datos](../01-arquitectura/DATA-MODEL.md)
- [Diccionario de datos](../01-arquitectura/DATA-DICTIONARY.md)
- [Entorno local](../02-desarrollo/LOCAL-DEV.md)
- [CI/CD](../03-operacion/CI-CD.md)
