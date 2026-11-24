# Informe de Auditoría EGM: Implementation Readiness & Code Quality (PROTOTIPO-1)

> **Documento auditado:** [`docs/02-desarrollo/PROTOTIPO-1.md`](PROTOTIPO-1.md) y base de código en rama `prototipo1` (`7b5f3c3` / `6640e55`).  
> **Protocolo:** EGM Goldfish Review Protocol (`egm-implementation-readiness`, `egm-codebase-details`).  
> **Fecha:** 2026-11-23.  
> **Veredicto:** **REQUIERE ACCIÓN (NO APTO PARA PRODUCCIÓN SIN REMEDIACIÓN)**.

---

## 1. Resumen Ejecutivo

El prototipo actual (`prototipo1`) es un logro importante como **prueba de concepto funcional e integrada de extremo a extremo** (Docker Compose, LocalStack, Keycloak, 13 microservicios compilando, Lambdas y tres interfaces web operativas).

Sin embargo, desde la perspectiva de **calidad de código, estándares de arquitectura del proyecto ([`AGENTS.md`](../../AGENTS.md)), principios SOLID, Ley de Deméter y cobertura de pruebas**, la base de código presenta una deuda técnica severa:

1. **0% de Cobertura de Pruebas en los 13 nuevos microservicios**: Ninguno de los 13 servicios bajo `services/*` cuenta con pruebas unitarias o de integración (`src/test` no existe o está vacío); las únicas pruebas existentes pertenecen al monolito original `core-fleet`.
2. **Antipatrón de Servicio Procedural Monolítico (Violación de Arquitectura Hexagonal y SRP)**: Cada microservicio consta de un único `Controller` y una única clase `Service` masiva con SQL crudo concatenado en `JdbcTemplate`, manejando a la vez lógica de dominio, persistencia, parsing JSON e I/O sin entidades de dominio, puertos ni adaptadores.
3. **Ausencia del estándar RFC 7807 (`ProblemDetail`)**: La gestión de errores se hace mediante diccionarios JSON ad-hoc en lugar de `org.springframework.http.ProblemDetail`.
4. **Violaciones de la Ley de Deméter / Tell, Don't Ask**: Controladores y servicios desempaquetan y reconstruyen registros campo a campo en lugar de delegar las operaciones al modelo.
5. **Falta de Tolerancia a Fallos (Resilience4j) y Contratos OpenAPI**.

---

## 2. Hallazgos Detallados por Severidad

### 🔴 Hallazgos Bloqueantes (Blockers)

#### 1. Cobertura de Pruebas Nula en los 13 Microservicios
- **Ámbito afectado:** Todos los servicios (`availability`, `reservations`, `rides`, `inventory`, `identity`, `user-profiles`, `telemetry`, `incidents`, `maintenance`, `relocation`, `history`, `analytics`, `zones`).
- **Regla vulnerada:** Regla 5 (*Pruebas y Calidad: JUnit 5, AssertJ, Testcontainers*).
- **Detalle:** Reconocido en el propio `PROTOTIPO-1.md:154` (*"Tests automáticos de los servicios nuevos: Pendiente"*). No existe ninguna prueba que garantice que las 16 transiciones de ciclo de vida en `availability` o las reservas en `reservations` funcionen tras una modificación o refactorización.
- **Acción requerida:** Crear suite de pruebas unitarias con JUnit 5 + AssertJ para la lógica de transiciones, y pruebas de integración con Testcontainers PostgreSQL para los flujos críticos (`availability`, `reservations`, `rides`).

#### 2. Incumplimiento de Arquitectura Hexagonal (Ports & Adapters) y Principio de Responsabilidad Única
- **Ámbito afectado:** Todas las clases `*Service.java` en `services/*`.
- **Regla vulnerada:** Reglas 3 y 4 (*Hexagonal Architecture: Ports & Adapters, SRP, ISP, Interfaces sobre Herencia*).
- **Detalle:** `AvailabilityService.java` concentra en 358 líneas: queries SQL con concatenación de strings, mapeo de ResultSet, validación de reglas de negocio de máquina de estados, manejo de eventos de EventStore y serialización JSON. No existen interfaces de casos de uso (`UseCases`), entidades de dominio aisladas ni puertos de repositorio.
- **Acción requerida:** Desacoplar cada servicio en:
  - `domain`: Modelos inmutables (Java Records) y máquina de estados pura.
  - `ports.in`: Interfaces atómicas de casos de uso.
  - `ports.out`: Interfaces para persistencia y publicación de eventos.
  - `adapters.in.web`: Controladores REST.
  - `adapters.out.jdbc`: Adaptadores de persistencia con Spring Data / JDBC.

---

### 🟡 Hallazgos Mayores (Major)

#### 3. Errores REST no alineados con RFC 7807 (`ProblemDetail`)
- **Ámbito afectado:** `libs/platform-common` (`ApiException`, `GlobalExceptionHandler`) y controladores REST.
- **Regla vulnerada:** Regla 4 (*APIs REST: RFC 7807 ProblemDetail*).
- **Detalle:** Se devuelven mapas de error personalizados con `{ "error": ..., "message": ... }` en lugar del formato estándar de Spring Boot 3 (`ProblemDetail`).
- **Acción requerida:** Actualizar `GlobalExceptionHandler` y `ApiException` para producir `org.springframework.http.ProblemDetail` con `title`, `status`, `detail`, `type` e `instance`.

#### 4. Violación de Ley de Deméter y Tell, Don't Ask
- **Ámbito afectado:** Controladores y servicios al proyectar datos (e.g. `AvailabilityService.java` y `AvailabilityController.java`).
- **Regla vulnerada:** Regla 3 (*Ley de Deméter: Tell, Don't Ask*).
- **Detalle:** Para sanitizar un vehículo antes de devolverlo a la API pública, el controlador o servicio lee sus 12 campos y construye manualmente una tupla con campos nulos en vez de pedirle al modelo `vehicle.toPublicView()`.
- **Acción requerida:** Añadir métodos de proyección y transformación en el propio registro/modelo de dominio.

#### 5. Ausencia de Tolerancia a Fallos (Resilience4j)
- **Ámbito afectado:** Llamadas salientes a Keycloak (`IdentityService`), envío de eventos y llamadas entre servicios.
- **Regla vulnerada:** Regla 4 (*Tolerancia a fallos con Resilience4j Circuit Breakers y Retries*).
- **Detalle:** Las llamadas HTTP y el envío a SQS carecen de interceptores de circuit breaker o políticas de reintento estructuradas.
- **Acción requerida:** Incorporar `resilience4j-spring-boot3` y anotar las integraciones I/O con `@CircuitBreaker` y `@Retry`.

---

### 🟢 Hallazgos Menores (Nit)

#### 6. Falta de Documentación OpenAPI 3.0
- **Ámbito afectado:** Controladores en `services/*`.
- **Regla vulnerada:** Regla 4 (*Documentación OpenAPI 3.0 mediante anotaciones SpringDoc*).
- **Detalle:** Los endpoints carecen de `@Operation`, `@ApiResponse` y descripciones de parámetros.

#### 7. Strings Mágicos y SQL Dinámico Frágil
- **Ámbito afectado:** `AvailabilityService`, `MaintenanceService`, etc.
- **Detalle:** Uso de sentencias SQL ensambladas con cadenas de texto (`WHERE 1 = 1 AND ...`) y literales repetidos sin constantes unívocas.

---

## 3. Plan de Remediación Priorizado (Roadmap)

| Fase | Objetivo | Entregables |
|---|---|---|
| **Fase 1: Red de Seguridad de Pruebas** | Proteger flujos críticos con tests antes de refactorizar | Pruebas unitarias para máquinas de estados en `availability`, `reservations` y `rides`. Pruebas de integración con Testcontainers. |
| **Fase 2: Refactorización a Arquitectura Hexagonal** | Separar dominio de infraestructura | Descomponer servicios monolíticos en `domain` (records inmutables), `ports` (in/out) y `adapters` (REST / JDBC). |
| **Fase 3: Estandarización REST & Resiliencia** | Cumplir RFC 7807 y tolerancia a fallos | Migrar `platform-common` a `ProblemDetail`. Incorporar Resilience4j en clientes y outbox. |
| **Fase 4: Contratos OpenAPI & Documentación** | Documentar y versionar contratos | Añadir anotaciones SpringDoc y generar especificaciones OpenAPI en `contracts/`. |
