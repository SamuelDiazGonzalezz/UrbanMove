# Deuda técnica y hoja de ruta — Prototipo 1

> **Fecha:** 2026-11-24
> **Rama:** `prototipo1`
> **Ámbito:** los 13 servicios, `libs/platform-common`, 5 Lambdas, Terraform, Compose, CI y documentación.
> **Estado:** documento de trabajo aprobado para ejecución por un agente en modo *build*.
> **Sustituye como hoja de ruta operativa** a [PLAN.md](PLAN.md) y [ACTION-PLAN.md](ACTION-PLAN.md)
> para la fase Prototipo 1. No los borra: los deja como plan histórico de la Fase 0.
>
> **Documento hermano (prioritario):** [TERRAFORM-FULL.md](../03-operacion/TERRAFORM-FULL.md),
> el plan para pasar de “Terraform de mensajería” a **Terraform de toda la plataforma**.

---

## 0. Cómo usar este documento

Este documento responde a tres preguntas:

1. **Qué deuda hay**, con evidencia verificable y no con impresiones.
2. **En qué orden se ataca**, para que un agente en build pueda ejecutar sin volver a decidir.
3. **Cómo se demuestra cada cosa**, porque la rúbrica puntúa evidencias, no intenciones.

Reglas de lectura:

- Si un documento choca con este, manda este documento + el código de la rama `prototipo1`.
- `docs/02-desarrollo/PROTOTIPO-1.md` describe **qué hay**; este documento describe **qué falta y cómo cerrarlo**.
- Los documentos marcados como históricos (`core-fleet`) no deben usarse para implementar.
- La rúbrica y sus pesos están en [PLAN.md](PLAN.md), sección 25; la matriz de evidencia está en la sección 9.

---

## 1. Veredicto ejecutivo

El prototipo **ya funciona de punta a punta**: 13 servicios, EventBridge + SQS, 5 Lambdas, Data Lake,
7 data marts y tres interfaces. La deuda ya no es “falta de producto”; es:

| Dimensión | Situación | Consecuencia |
|---|---|---|
| **Producto** | Flujo completo implementado y demostrable a mano | Base sólida para la defensa |
| **Calidad automatizada** | 15 tests reales en 3 de 13 servicios; 28 contando el legado | Cualquier refactor rompe sin red |
| **Contratos** | `contracts/` no existe | Incumple la regla “sin contrato no está terminado” (16% + 12% de rúbrica) |
| **Arquitectura de código** | El núcleo es procedural con SQL crudo | Incumple `AGENTS.md` (hexagonal, SRP, Tell-Don't-Ask) |
| **Documentación** | Describe dos arquitecturas y tiene enlaces rotos | Un agente puede trabajar sobre `core-fleet` por error |
| **Operación** | Observabilidad mínima y Terraform parcial | DevOps 10%, observabilidad 6% y escalabilidad 9% sin evidencia |
| **Nube** | Terraform solo cubre bus, colas, Lambdas y S3 | No hay despliegue completo reproducible en AWS |

**Decisión estratégica:** no se añaden servicios ni funcionalidad nueva hasta cerrar **contratos,
tests y Terraform full**. La prioridad es convertir lo que ya funciona en algo **verificable y
rubric-compliant**.

---

## 2. Estado verificado (evidencia, no documentación)

Verificado el 2026-11-24 en `prototipo1` (`90f8213`):

| Comprobación | Resultado |
|---|---|
| Módulos Maven | 13 servicios + `libs/platform-common` + `services/core-fleet` legado |
| Tests en el sistema real | **15** (6 `availability`, 5 `reservations`, 4 `rides`) |
| Tests en el legado | **13** en `core-fleet` |
| Servicios sin `src/test` | **10 de 13**: `identity`, `user-profiles`, `zones`, `inventory`, `telemetry`, `incidents`, `maintenance`, `relocation`, `history`, `analytics` |
| `mvn -B -ntp test` | **Falla en local**: `FleetConcurrencyIntegrationTest` no encuentra Docker (Testcontainers en la fase `test`) |
| `contracts/` | **No existe** |
| SpringDoc / Resilience4j / JaCoCo / ArchUnit / Awaitility | **No configurados** |
| `mvnw` | **No existe** (los docs mencionan `./mvnw`) |
| `core-fleet` | En el reactor y en CI; excluido solo del Dockerfile (`-pl '!services/core-fleet'`) |
| CI | Solo `mvn clean test` + Terraform; **no construye imágenes ni valida contratos** |
| Prometheus / Grafana | **No están en Compose** |
| Carpetas de operación | Duplicadas: `docs/03-operacion` y `docs/03-operaciones` |
| Enlaces relativos rotos | 4 (informe EGM y `docs/README.md`) |
| Lambdas | 5; ninguna con tests; 4 necesitan red (RDS o servicios) |

### 2.1. Hallazgos que condicionan el orden

- **El legado contamina la señal de calidad.** 13 de los 28 tests pertenecen a `core-fleet`, un
  servicio que no se despliega. Sin separarlo, la cobertura real parece mucho mejor de lo que es.
- **El build no es reproducible sin Docker.** Mezclar Testcontainers en `mvn test` hace que el
  comando que usa CI no funcione en un portátil sin Docker encendido.
- **La documentación de P0.2 describe otra implementación.** El outbox con intentos, backoff y
  dead-letter está en `core-fleet`; `platform-common` tiene una versión simplificada. Un agente
  puede creer que la deuda está cerrada cuando no lo está para los 13 servicios.
- **Terraform cubre la mensajería, no la plataforma.** No hay VPC, RDS, ECR, ECS, ALB, Keycloak,
  gateway, secretos ni observabilidad. Ver [TERRAFORM-FULL.md](../03-operacion/TERRAFORM-FULL.md).

---

## 3. Inventario de deuda

### 3.1. P0 — Bloquea “verde, demostrable y sin ambigüedad”

| ID | Deuda | Evidencia | Impacto | Tamaño |
|---|---|---|---|---|
| **P0.1** | Tests de integración mezclados con unitarios | `services/core-fleet/.../FleetConcurrencyIntegrationTest.java`; no hay Failsafe; `mvn test` rojo sin Docker | Build no reproducible y PRs frágiles | S |
| **P0.2** | `core-fleet` legado convive con el prototipo | `pom.xml` raíz, Dockerfile lo excluye, 15/28 tests son suyos | Confusión de arquitectura y coste de CI | S |
| **P0.3** | Contratos inexistentes | No hay `contracts/`; no hay SpringDoc | 16% arquitectura + 12% eventos sin evidencia | M |
| **P0.4** | Cobertura casi nula del sistema real | 10/13 servicios sin `src/test`; `platform-common` y Lambdas sin tests | Cualquier cambio es a ciegas | L |
| **P0.5** | CI incompleto | `.github/workflows/ci.yml`: solo `test` + `terraform` | No detecta Dockerfile roto, contratos inválidos ni cobertura | S/M |
| **P0.6** | La documentación no es fuente de verdad | README raíz y `docs/README.md` desactualizados; carpetas duplicadas; 4 enlaces rotos | Un agente puede implementar sobre el documento equivocado | S |
| **P0.7** | Dos arquitecturas documentadas | ADR 0012, `p0-remediation-design.md` y `ESTADO-…P0.2` hablan de `core-fleet` | Riesgo alto de tocar el módulo legado | S |

### 3.2. P1 — Calidad, estándares y fiabilidad

| ID | Deuda | Evidencia | Impacto |
|---|---|---|---|
| **P1.1** | `availability` es un servicio procedural de 316 líneas | `AvailabilityService.java`: SQL concatenado, `Map<String,Object>`, `Change` mutable, `Math.random()`, `summary()` devuelve mapas | Incumple SRP, hexagonal y Tell-Don't-Ask |
| **P1.2** | `reservations` y `rides` repiten el patrón | 214 y 200 líneas; sin dominio, puertos ni DTOs de salida | Difícil de testear y de evolucionar |
| **P1.3** | Sin RFC 7807 | `GlobalExceptionHandler` devuelve `{error, message, correlationId}`; nginx devuelve JSON ad hoc | Incumple `AGENTS.md` y rompe clientes estándar |
| **P1.4** | Concurrencia optimista sin reintento | `applyChange` lanza `IllegalStateException` → 500; `EventStore.append` usa `MAX(aggregate_version)+1` | Errores esporádicos bajo carga |
| **P1.5** | Sin Resilience4j | `KeycloakClient` y `RestClient` sin retry/CB; SQS/EventBridge sin política estructurada | Un fallo externo degrada el flujo completo |
| **P1.6** | Fiabilidad de eventos mejorable | `OutboxRelay` hace I/O de red **dentro** de la transacción con `FOR UPDATE SKIP LOCKED`; sin intentos/backoff/DLQ; `EventConsumer` reintenta un mensaje ilegible 5 veces con un log que dice “descartado a la DLQ” | Pérdida o bloqueo de eventos; diagnóstico engañoso |
| **P1.7** | Payloads y respuestas sin tipos | `Map<String,Object>` en eventos y en `summary()` | Sin contrato, sin validación, sin esquema |

### 3.3. P2 — Rúbrica, operación y nube

| ID | Deuda | Evidencia | Impacto |
|---|---|---|---|
| **P2.1** | Observabilidad incompleta | Solo Actuator `/health`; no Micrometer, Prometheus, Grafana ni métricas de outbox/DLQ | 6% de rúbrica y ADR 0005 |
| **P2.2** | Seguridad por aislamiento de red | `/internal/**` y `/actuator/**` con `permitAll`; password grant; credenciales admin por entorno; sin PKCE ni rate limit | Aceptable en local, insuficiente en AWS |
| **P2.3** | Sin E2E, carga ni evidencias de fallo | Demos manuales en `PROTOTIPO-1.md`; no hay Locust ni scripts de evidencia | 10% tolerancia + 9% escalabilidad sin prueba |
| **P2.4** | Terraform parcial | Solo bus, colas, Lambdas y S3; sin VPC, RDS, ECR, ECS, ALB, Keycloak, gateway, secretos ni CD | DevOps 10% incompleto; no hay despliegue full reproducible |
| **P2.5** | Catálogo de eventos y datos desactualizado | `EVENTS.md` no recoge los 12 eventos nuevos; `DATA-MODEL`/`DATA-DICTIONARY` no reflejan las tablas reales | Documentación arquitectónica incoherente |
| **P2.6** | Sin despliegue ni coste demostrado | `staging.tfvars` existe, pero no se ha aplicado; no hay backend remoto, OIDC ni tabla de coste con datos | Sección obligatoria del PDF |

### 3.4. Deuda documental concreta (para cerrar en PR-0)

| Documento | Problema |
|---|---|
| `README.md` (raíz) | “Estructura prevista (aún no creada)”; checklist de Fase 0 sin marcar; estado desactualizado |
| `docs/README.md` | No lista `PROTOTIPO-1.md`, bitácora, informe EGM ni guía AWS; 2 enlaces rotos |
| `docs/03-operacion` vs `docs/03-operaciones` | Dos carpetas para lo mismo |
| `MANUAL-PRUEBAS-MVP.md` | Describe el MVP `core-fleet`, no los 13 servicios |
| `PLAN.md` / `ACTION-PLAN.md` | Siguen la Fase 0 y no reflejan el prototipo |
| `ESTADO-Y-PENDIENTES-2026-11-21*.md` | Estado del legado, no del prototipo |
| `p0-remediation-design.md` | Diseño del legado; se puede confundir con deuda vigente |
| ADR 0012 | “Aceptado”; en la práctica está sustituido por los 13 servicios |
| Informe EGM | 2 enlaces rotos; escrito contra `7b5f3c3`, antes de los tests actuales |

---

## 4. Decisiones tomadas

| # | Decisión | Motivo |
|---|---|---|
| **D1** | `core-fleet` pasa a **legado explícito**: código y tests se conservan, pero fuera del reactor por defecto (perfil `legacy`) y fuera de la arquitectura vigente | Evita confusión y acelera CI sin perder la historia |
| **D2** | La fuente de verdad es `PROTOTIPO-1.md` + código + Terraform | Los documentos de Fase 0 describen otro sistema |
| **D3** | Contratos **generados** con SpringDoc y versionados en `contracts/` | Evita contratos inventados que no coinciden con la implementación |
| **D4** | Refactor hexagonal **solo del núcleo** (`availability` completo; `reservations` y `rides` con capas mínimas) | Refactorizar 13 servicios es la forma más rápida de romper la demo |
| **D5** | Tests separados por fase: unitarios con Surefire (`mvn test`, sin Docker) e integración con Failsafe (`mvn verify`, con Testcontainers) | Build reproducible y CI honesto |
| **D6** | Observabilidad **añadida**, no sustitutiva: Prometheus + Grafana conviven con la app `dashboard/` | La app es una de las tres interfaces; Grafana es la evidencia técnica |
| **D7** | **Terraform full** es el objetivo principal de la parte cloud, no un extra | DevOps 10% + escalabilidad + coste + despliegue reproducible |
| **D8** | AWS real se aplica cuando contratos, tests y observabilidad estén verdes | Desplegar antes solo multiplica el coste de depuración |

---

## 5. Roadmap ejecutable

Cada bloque es un PR pequeño, con criterio de aceptación y evidencia. Los tamaños son
orientativos: **S** < 1 sesión, **M** 1–2 sesiones, **L** 2–4 sesiones.

### 5.1. Vista general

| PR | Objetivo | Deuda que cierra | Depende de | Tamaño |
|---|---|---|---|---|
| **PR-0** | Verdad documental y legado | P0.2, P0.6, P0.7 | — | S |
| **PR-1** | Build reproducible y CI real | P0.1, P0.2, P0.5 | PR-0 | S/M |
| **PR-2** | Contratos OpenAPI, eventos y AsyncAPI | P0.3 | PR-0, PR-1 | M |
| **PR-3** | Red de seguridad de pruebas | P0.4 | PR-1 | M/L |
| **PR-4** | RFC 7807 y DTOs tipados | P1.3, P1.7 | PR-2 | M |
| **PR-5** | Hexagonal en el núcleo | P1.1, P1.2, P1.4 | PR-3, PR-4 | L |
| **PR-6** | Fiabilidad de eventos | P1.6 | PR-3 | M |
| **PR-7** | Observabilidad y resiliencia | P1.5, P2.1, P2.3 | PR-3, PR-6 | M |
| **PR-8** | Verdad documental final y rúbrica | P0.6, P2.5 | PR-2…PR-7 | M |
| **TF-0…TF-6** | **Terraform full** (ver documento hermano) | P2.4, P2.6 | TF-0 desde ya; cutover tras PR-3/PR-7 | L |

Orden recomendado:

```text
PR-0 ─┬─ PR-1 ─┬─ PR-3 ─┬─ PR-5
      │        │        └─ PR-6
      ├─ PR-2 ─┘
      └─ TF-0 ─ TF-1 ─ TF-2 ─ TF-3 ─ TF-5 ─ TF-6
PR-4 ← PR-2
PR-7 ← PR-3, PR-6
PR-8 ← todos
```

> **Nota:** el trabajo de Terraform full puede empezar en paralelo desde TF-0 (estado remoto y
> estructura), pero **no se aplica en AWS real** hasta que PR-3 y PR-7 estén verdes.

---

### PR-0 — Verdad documental y legado (S)

**Objetivo:** que cualquier persona o agente sepa en dos minutos qué arquitectura está vigente.

Tareas:

1. Crear ADR 0013 "El Prototipo 1 sustituye a `core-fleet`" y marcar ADR 0012 como **sustituido**. ✅ (2026-11-24)
2. Unificar `docs/03-operacion` y `docs/03-operaciones` en una sola carpeta. ✅ (2026-11-24)
3. Corregir los 4 enlaces relativos rotos. ✅ (2026-11-24)
4. Actualizar `docs/README.md`: estructura real, índice de documentos nuevos y orden de lectura.
5. Actualizar `README.md` raíz: estructura existente, estado real y enlaces a esta hoja de ruta.
6. Banner de “documento histórico (core-fleet)” en:
   - `docs/00-gestion/ESTADO-Y-PENDIENTES-2026-11-21.md`
   - `docs/00-gestion/ESTADO-Y-PENDIENTES-2026-11-21-P0.2.md`
   - `docs/01-arquitectura/p0-remediation-design.md`
   - `docs/02-desarrollo/MANUAL-PRUEBAS-MVP.md`
7. Mover `core-fleet` a un perfil Maven `legacy` (no borrar código ni tests).

**Aceptación:**

- Verificador de enlaces sin errores.
- `mvn test` ya no compila `core-fleet` por defecto.
- Un lector distingue la arquitectura vigente sin abrir el código.

---

### PR-1 — Build reproducible y CI real (S/M)

**Objetivo:** que `mvn test` funcione en cualquier portátil y que el CI detecte lo que hoy no detecta.

Tareas:

1. Añadir Maven Failsafe y renombrar `FleetConcurrencyIntegrationTest` → `*IT`.
2. `mvn test` = unitarios, sin Docker. `mvn verify` = unitarios + integración.
3. Añadir `mvnw`/`mvnw.cmd` y `.mvn/wrapper` (o eliminar la referencia de los docs).
4. CI:
   - job `unit` con `mvn -B -ntp test`;
   - job `integration` con `mvn -B -ntp verify` y Docker;
   - job `images` con `docker compose config` + build de las 13 imágenes;
   - caché de Maven y Dependabot.
5. Publicar informes de tests como artefactos.

**Aceptación:**

- `mvn -B -ntp test` verde sin Docker.
- `mvn -B -ntp verify` verde con Docker.
- El CI construye las 13 imágenes y falla si el Dockerfile se rompe.

---

### PR-2 — Contratos OpenAPI, eventos y AsyncAPI (M)

**Objetivo:** que cada endpoint y cada evento tengan contrato versionado y validado.

Tareas:

1. Añadir SpringDoc a los 13 servicios (dependencia gestionada en el POM padre).
2. Script `scripts/export-contracts.*` que vuelque `/v3/api-docs` a `contracts/openapi/<servicio>.json`.
3. Generar `contracts/events/<Evento>.v1.json` para **todos** los eventos de `events.tf`.
4. Crear `contracts/asyncapi/urbanmove.yaml` con bus, colas, DLQ, productores y consumidores.
5. CI: Redocly lint + AsyncAPI validate + comprobación de que no falta ningún evento del catálogo.
6. Enlazar los contratos desde `docs/01-arquitectura/EVENTS.md` y `PROTOTIPO-1.md`.

**Aceptación:**

- 13 OpenAPI versionados y válidos.
- Todos los `detail-type` de `infra/terraform/events.tf` tienen JSON Schema.
- AsyncAPI válido y con la misma topología que Terraform.
- CI falla si un contrato deja de ser válido o falta un evento.

---

### PR-3 — Red de seguridad de pruebas (M/L)

**Objetivo:** poder refactorizar sin miedo.

Tareas:

1. `libs/test-support` con extensión JUnit 5 para PostgreSQL y *fixtures* (composición, no clase base abstracta).
2. Tests unitarios en los 13 servicios:
   - reglas de estado y umbrales de batería;
   - validaciones de identidad, zonas, inventario, telemetría e incidentes;
   - cálculo de tarifas y reglas de mantenimiento/recolocación.
3. Tests de integración con Testcontainers:
   - doble reserva concurrente → 1 éxito + 1 `409`;
   - idempotencia de inicio/fin de viaje;
   - consumo de eventos + outbox en `availability`;
   - ingesta idempotente en `history`;
   - consultas de `analytics` sobre marts.
4. JaCoCo: informe + umbral global del 50% (empezar como aviso y endurecer en PR-8).
5. Awaitility para asincronía; prohibido `Thread.sleep()`.

**Aceptación:**

- 13/13 servicios con al menos un test unitario y uno de integración.
- `mvn verify` verde.
- La doble reserva concurrente queda demostrada por un test, no por una demo manual.

---

### PR-4 — RFC 7807 y DTOs tipados (M)

**Objetivo:** cumplir el estándar de errores y dejar de mover mapas sin contrato.

Tareas:

1. `GlobalExceptionHandler` → `ProblemDetail` con `type`, `title`, `status`, `detail`, `instance`, `code` y `correlationId`.
2. nginx devuelve `application/problem+json` en 404/503.
3. Sustituir `Map<String,Object>` por records en:
   - payloads de eventos de los servicios del núcleo;
   - respuestas de `summary()` y proyecciones públicas;
   - `Change` de `availability` (pasar a un tipo inmutable).
4. Tests de *slice* que verifiquen `application/problem+json` en 400/403/404/409/500.

**Aceptación:**

- Ningún error REST devuelve `{ "error": ... }` ad hoc.
- Los payloads del núcleo son records y se serializan según el contrato.
- Tests de error verdes en los 13 servicios.

---

### PR-5 — Hexagonal en el núcleo (L)

**Objetivo:** que al menos el servicio más crítico sea un ejemplo defendible de la arquitectura del proyecto.

Tareas:

1. `availability`:
   - `domain/`: `Vehicle`, `VehicleStateMachine` puro (9 estados y tabla de transiciones);
   - `ports/in/`: casos de uso atómicos;
   - `ports/out/`: estado y publicación de eventos;
   - `adapters/in/web`, `adapters/in/events`, `adapters/out/jdbc`.
2. `reservations` y `rides`: caso de uso + repositorio + publicación de eventos; controlador sin lógica.
3. Reintento acotado en conflicto de versión; eliminar `Math.random()` del dominio.
4. Reglas ArchUnit:
   - dominio sin Spring, JDBC ni Jackson;
   - adaptadores sin cruces;
   - casos de uso sin controladores.
5. Un test por transición de estado y por regla de negocio.

**Aceptación:**

- ArchUnit verde.
- `AvailabilityService` desaparece o queda como orquestador de menos de 50 líneas.
- El controlador no contiene reglas de dominio.
- El resto de servicios mantiene capa de aplicación + JDBC, sin prometer hexagonal completo.

> **Nota de alcance:** no se refactorizan los 13 servicios. Se prioriza el núcleo y se documenta
> el patrón para el resto.

---

### PR-6 — Fiabilidad de eventos (M)

**Objetivo:** que un fallo de red no pierda ni bloquee eventos.

Tareas:

1. `OutboxRelay`:
   - reclamar lote en transacción corta;
   - publicar **fuera** de la transacción;
   - registrar intento, backoff y `DEAD_LETTER`;
   - health indicator y métricas (pendientes, publicados, lag).
2. `EventConsumer`:
   - mensaje ilegible → DLQ explícita o *parking*;
   - log correcto (no decir “descartado” si se reintenta);
   - métricas de consumidos, fallidos y DLQ.
3. Script `scripts/dlq-redrive.*` y documentación de uso.
4. Test de integración con LocalStack: publicar, consumir, idempotencia y dead-letter.

**Aceptación:**

- Una caída de EventBridge no pierde eventos.
- Un mensaje corrupto acaba en DLQ y se reprocesa con el script.
- El health de outbox refleja el estado real.

---

### PR-7 — Observabilidad y resiliencia (M)

**Objetivo:** demostrar tolerancia a fallos y métricas, no solo describirlas.

Tareas:

1. Micrometer + Prometheus + Grafana en un perfil de Compose (`observability`).
2. Dashboard provisionado: flota, reservas, viajes, mantenimiento, recolocación, DLQ, outbox y salud.
3. Métricas de negocio y técnicas: reservas aceptadas/rechazadas, viajes, latencia HTTP, outbox lag, DLQ depth.
4. Resilience4j en:
   - `KeycloakClient` (timeout, retry, circuit breaker);
   - `RestClient` entre servicios;
   - clientes SQS/EventBridge (reintentos con jitter, fuera de transacción).
5. 2–3 escenarios de fallo con evidencia:
   - parar `history` (eventos se acumulan y se recuperan);
   - parar LocalStack (outbox crece y se drena al volver);
   - Keycloak caído (circuit breaker abierto y respuesta degradada).
6. Locust con escenario de reservas/viajes y umbrales.

**Aceptación:**

- `/actuator/prometheus` expuesto y scrapeado.
- Dashboard y capturas enlazados desde `docs/`.
- Dos fallos demostrados con logs/métricas.
- Informe de carga con cuello de botella identificado.

---

### PR-8 — Verdad documental final y rúbrica (M)

**Objetivo:** que cada fila de la rúbrica tenga una evidencia enlazada.

Tareas:

1. Actualizar `EVENTS.md`, `DATA-MODEL.md`, `DATA-DICTIONARY.md` y `SERVICE-CLASSIFICATION.md` al sistema real.
2. Reescribir `MANUAL-PRUEBAS-MVP.md` para los 13 servicios (o crear `MANUAL-PRUEBAS-PROTOTIPO-1.md`).
3. Índice de evidencias: CI, cobertura, contratos, Grafana, DLQ, S3, capturas.
4. Actualizar `README.md`, `PROTOTIPO-1.md` y `BITACORA-PROTOTIPO-1.md` con el estado final.
5. PDF/guion de defensa y reparto.
6. Coste y limpieza (enlazado con TF-6).

**Aceptación:**

- Matriz de rúbrica de la sección 9 completa.
- Demos de menos de 10 minutos reproducibles desde cero.

---

## 6. Terraform full (prioridad estratégica)

**Resumen:** hoy Terraform solo gestiona EventBridge, SQS + DLQ, las 5 Lambdas y el bucket S3.
La plataforma (VPC, RDS, ECR, ECS, ALB, Keycloak, gateway, secretos, observabilidad) vive solo en
Docker Compose. Eso deja **DevOps y despliegue (10%)**, **escalabilidad (9%)** y parte de
**arquitectura (16%)** sin evidencia cloud.

**Objetivo:** que `terraform apply` cree la plataforma completa y que el mismo código funcione en
LocalStack y en AWS real.

Trabajo, en orden:

| Fase | Qué se hace | Aceptación |
|---|---|---|
| **TF-0** | Estado remoto (S3 + DynamoDB), estructura de módulos, entornos y rol OIDC para GitHub | `terraform init` remoto y `plan` reproducible |
| **TF-1** | Red: VPC, subredes, IGW/NAT, security groups, endpoints | Plan de red limpio y mínimo privilegio |
| **TF-2** | Datos: RDS PostgreSQL, bases por servicio, Secrets Manager, backups (ADR 0006) | 13 bases + `keycloak_db` creadas y accesibles |
| **TF-3** | Registro y cómputo: ECR, ECS Fargate, Keycloak, gateway, ALB, Cloud Map, autoscaling | 15 tareas sanas y `/api` enrutado |
| **TF-4** | Mensajería y Lambdas en AWS: VPC, endpoints, permisos, alarmas de DLQ | Flujo evento → cola → Lambda → S3 |
| **TF-5** | CD: build/push de 13 imágenes, migraciones, despliegue y smoke test | Pipeline verde y smoke E2E |
| **TF-6** | Cutover, coste, `destroy` y evidencias | Demo en AWS y coste documentado |

**Hallazgo bloqueante para AWS:** las Lambdas `mart_builder`, `demand_relocation`,
`maintenance_detection` y `reservation_expiration` necesitan red (RDS o servicios) y hoy su
entorno apunta a `postgres`, `reservations:8080` y `maintenance:8080`. En AWS no existen esos
nombres: hay que **parametrizar el entorno, adjuntar VPC y usar RDS / ALB interno / Cloud Map**.
Está detallado en [TERRAFORM-FULL.md](../03-operacion/TERRAFORM-FULL.md), sección 4.

**Decisión de coste:** ECS Fargate es la opción “cloud native” y la recomendada para la rúbrica;
EC2 + Docker Compose es el plan B si el crédito de AWS Academy es limitado. El documento incluye
estimación, riesgos y criterios para elegir.

---

## 7. Matriz de rúbrica → evidencia

| Criterio (PLAN §25) | Peso | Estado hoy | Evidencia que lo cierra |
|---|---:|---|---|
| Arquitectura | 16% | Parcial | C4 + catálogo + ADR 0013 + `contracts/` (PR-2, PR-0) |
| Implementación | 16% | Alto | 13 servicios, API, estados, persistencia, Docker (ya existe) |
| Eventos y serverless | 12% | Parcial | Catálogo actualizado + outbox + AsyncAPI (PR-2, PR-6) |
| Capa analítica | 14% | Alto | Lake + 7 marts + dashboard (ya existe; falta evidencia) |
| Tolerancia a fallos | 10% | Bajo | Outbox/DLQ/idempotencia + 2 fallos demostrados (PR-6, PR-7) |
| Escalabilidad | 9% | Bajo | Stateless + ECS/autoscaling + cuellos de botella (TF-3, PR-7) |
| DevOps y despliegue | 10% | Medio | CI completo + imágenes + Terraform full + CD (PR-1, TF-0…TF-5) |
| Observabilidad | 6% | Bajo | Micrometer + Prometheus + Grafana + salud (PR-7) |
| Documentación y defensa | 7% | Medio | Docs al día + PDF + demo + reparto (PR-8) |

---

## 8. Reglas para el agente en build

1. Leer `PROTOTIPO-1.md` y este documento antes de tocar código.
2. No tocar `core-fleet` salvo para el perfil `legacy`.
3. No cambiar rutas `/api/*`, nombres de eventos ni nombres de colas sin actualizar a la vez
   `infra/terraform/events.tf`, `contracts/` y la documentación.
4. `mvn test` antes de cada commit; `mvn verify` antes de cada PR.
5. Un PR = un objetivo de este documento. Si hace falta tocar dos, se separa.
6. Cada PR incluye su evidencia (logs, capturas, informes) enlazada desde `docs/`.
7. Prohibido: H2, `Thread.sleep()`, secretos en el repositorio, `Math.random()` en dominio.
8. Los cambios de infraestructura van con `terraform fmt` + `validate` + `plan` en el mismo PR.

---

## 9. Riesgos y mitigaciones

| Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|
| El refactor del núcleo rompe la demo | Media | Alto | PR-3 antes de PR-5; tests por transición; PRs pequeños |
| AWS Academy sin crédito suficiente | Media | Alto | TF-2/TF-3 con plan B EC2; `destroy` tras la demo; presupuesto |
| ECS Fargate supera el coste | Media | Medio | 0,25 vCPU/0,5 GB, Fargate Spot, mínimo 1 tarea, apagar fuera de demo |
| Lambdas sin VPC fallan en AWS | Alta | Alto | TF-4 explícito; VPC + NAT o endpoints; pruebas de humo |
| RDS no crea 13 bases | Alta | Medio | `postgresql` provider o tarea de bootstrap; documentado en TF-2 |
| Contratos generados divergen del runtime | Media | Medio | Generación en CI + comparación con lo versionado |
| Dos arquitecturas vuelven a mezclarse | Media | Alto | PR-0 + ADR 0013 + banners + revisión de PRs |
| Falta de tiempo para PR-5/PR-7 | Alta | Medio | Recortar refactor a `availability` y observabilidad mínima |

---

## 10. Comandos de verificación

```bash
# Unitarios (no requieren Docker)
mvn -B -ntp test

# Unitarios + integración (requieren Docker)
mvn -B -ntp verify

# Solo imágenes
docker compose config
docker compose build

# Plataforma local completa
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8080/api/analytics/health

# Contratos
npx @redocly/cli lint contracts/openapi/*.json
npx @asyncapi/cli validate contracts/asyncapi/urbanmove.yaml

# Terraform
terraform -chdir=infra/terraform fmt -check -diff
terraform -chdir=infra/terraform validate
terraform -chdir=infra/terraform plan -var-file=environments/local.tfvars -input=false
```

---

## 11. Próximos 7 días

| Día | Objetivo |
|---|---|
| 1 | PR-0: ADR 0013, unificar carpetas, corregir enlaces, banners, `core-fleet` legacy |
| 2 | PR-1: Failsafe + `mvnw` + CI con unit/integration/images |
| 3 | PR-2: SpringDoc + exportación de contratos de los 13 servicios |
| 4 | PR-2: JSON Schemas + AsyncAPI + validación en CI |
| 5 | TF-0: estado remoto, estructura de módulos y OIDC |
| 6 | PR-3: `test-support` + unitarios de los servicios sin tests |
| 7 | PR-3: integración de doble reserva, viajes e idempotencia; revisión de la semana |

---

## 12. Enlaces

- [Prototipo 1](../02-desarrollo/PROTOTIPO-1.md) — qué hay implementado.
- [Bitácora del Prototipo 1](../02-desarrollo/BITACORA-PROTOTIPO-1.md) — qué se hizo y cuándo.
- [Informe EGM](../02-desarrollo/egm-implementation-readiness-report.md) — auditoría de calidad.
- [Terraform full](../03-operacion/TERRAFORM-FULL.md) — plan detallado de IaC completa.
- [Guía de migración a AWS](../03-operacion/AWS-MIGRATION-GUIDE.md) — estado actual de la migración.
- [CI/CD](../03-operacion/CI-CD.md) — pipelines y gates.
- [Pruebas](../02-desarrollo/TESTING.md) — estrategia de tests.
- [Estándares de código](../02-desarrollo/CODE-STANDARDS.md) — reglas de diseño.
- [Eventos](../01-arquitectura/EVENTS.md) — catálogo y sobre canónico.
- [Adr 0013](../01-arquitectura/adr/0013-prototipo1-sustituye-core-fleet.md) — arquitectura vigente.
