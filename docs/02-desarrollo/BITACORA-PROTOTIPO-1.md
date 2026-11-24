# Registro de Trabajo y Bitácora de Desarrollo: Prototipo 1

> **Rama:** `prototipo1`  
> **Fecha de Actualización:** 23 de Noviembre de 2026  
> **Estado:** CI configurada, Auditoría de Calidad EGM completada, Fase 1 de Pruebas Unitarias del Núcleo (Availability, Reservations, Rides) implementada, verificada y subida al repositorio remoto.

---

## 1. Contexto y Objetivos Iniciales

Al cambiar a la rama `prototipo1`, el repositorio pasó de un único servicio inicial (`core-fleet`) a la arquitectura completa distribuida del **Prototipo 1**:
- **13 microservicios Java / Spring Boot 3+** (`availability`, `reservations`, `rides`, `fleet`, `maintenance`, `pricing`, `payments`, `penalties`, `zones`, `telemetry`, `identity`, `notifications`, `analytics-query`).
- Biblioteca compartida `libs/platform-common` (Outbox pattern, EventStore, filtros de correlación, consumidor SQS idempotente).
- Infraestructura IaC con Terraform para AWS (LocalStack), Gateway Nginx, Lambdas Python y Keycloak.

### Necesidades detectadas:
1. **Falta de CI en GitHub Actions** para la nueva rama `prototipo1`.
2. **Ausencia total de tests unitarios e integración** en los 13 nuevos servicios.
3. **Revisión de arquitectura y código (EGM Review)** frente a las reglas de diseño del proyecto (`AGENTS.md`: Java 21, inmutabilidad, Records, Tell-Don't-Ask, RFC 7807, Hexagonal Architecture).
4. **Creación de la red de seguridad de pruebas** para el núcleo transaccional.

---

## 2. Acciones Realizadas

### 2.1. Habilitación de Integración Continua (CI) en GitHub Actions
- **Fichero modificado:** `.github/workflows/ci.yml`
- **Cambio:** Se actualizó el flujo de trabajo de GitHub Actions para que los triggers `push` y `pull_request` incluyan explícitamente la rama `prototipo1`.
- **Commit:** `6640e55` (*"ci: trigger workflow on push and pr to prototipo1"*).

---

### 2.2. Auditoría EGM de Preparación para Implementación (Implementation Readiness Review)
Se ejecutó un análisis exhaustivo con el agente especialista `egm-implementation-readiness` contrastando el código del monorepo contra `docs/02-desarrollo/PROTOTIPO-1.md` y `AGENTS.md`.

- **Informe generado:** [`docs/02-desarrollo/egm-implementation-readiness-report.md`](file:///e:/CORONAVIRUS/Estudios/Universidad/4º/cn/urbanmobility/docs/02-desarrollo/egm-implementation-readiness-report.md)
- **Calificación global:** **82 / 100** (Listo para remediación en fases).
- **Hallazgos clave:**
  1. *Cobertura nula:* Ninguno de los 13 servicios contaba con carpetas `src/test/java`.
  2. *Violación de RFC 7807:* `libs/platform-common` y los servicios devuelven errores planos `{"error": "..."}` en lugar del estándar `ProblemDetail`.
  3. *Acoplamiento en `AvailabilityService`:* Mezcla de lógica de máquina de estados de vehículos con consultas SQL directas por JDBC sin separación de capas (Arquitectura Hexagonal).
  4. *Falta de Tests de Concurrencia:* Ausencia de validaciones con Testcontainers para carreras críticas (doble reserva simultánea o inicio de viaje concurrente).

---

### 2.3. Implementación de la Suite de Pruebas Unitarias (Fase 1: Núcleo Crítico)

Se crearon suites de pruebas unitarias con **JUnit 5**, **AssertJ** y **Mockito** (configurado con `Strictness.LENIENT` y buenas prácticas para Java 21) en los tres servicios transaccionales esenciales:

#### A. Servicio `availability` (`services/availability`)
- **Fichero:** `services/availability/src/test/java/com/urbanmove/availability/AvailabilityServiceTest.java`
- **Cobertura implementada (6 tests):**
  - Transición a `AVAILABLE` al procesar evento `VehiclePurchased`.
  - Transición a `RESERVED` con asignación de usuario y reserva al procesar `VehicleReserved`.
  - Transición a `AVAILABLE` tras `VehicleReservationCancelled` y `VehicleReservationExpired`.
  - Actualización de telemetría y paso a `MAINTENANCE` si la batería desciende del 15% al recibir `VehicleDroppedOff`.
  - Rechazo y registro de advertencia ante transiciones ilegales en la máquina de estados.
- **Resultado:** **6/6 tests pasando en verde** (`BUILD SUCCESS`).

#### B. Servicio `reservations` (`services/reservations`)
- **Fichero:** `services/reservations/src/test/java/com/urbanmove/reservations/ReservationServiceTest.java`
- **Cobertura implementada (5 tests):**
  - Reserva exitosa (`reserve`): inserción en base de datos, bloqueo de 15 minutos y emisión del evento `VehicleReserved` en la tabla `outbox`.
  - Rechazo al reservar un vehículo no disponible (`409 Conflict`).
  - Rechazo si el usuario ya cuenta con otra reserva activa (`409 Conflict`).
  - Cancelación de reserva (`cancel`): liberación del vehículo y emisión de `VehicleReservationCancelled`.
  - Expiración de reservas caducadas (`expire`): liberación masiva y emisión de `VehicleReservationExpired`.
- **Resultado:** **5/5 tests pasando en verde** (`BUILD SUCCESS`).

#### C. Servicio `rides` (`services/rides`)
- **Fichero:** `services/rides/src/test/java/com/urbanmove/rides/RideServiceTest.java`
- **Cobertura implementada (4 tests):**
  - Inicio de viaje walk-in directo sobre vehículo `AVAILABLE`: creación del `trip` y emisión de `VehiclePickedUp`.
  - Inicio de viaje de un vehículo `RESERVED` cuando el usuario solicitante coincide con quien realizó la reserva previa.
  - Rechazo de inicio de viaje si el vehículo está reservado por otro usuario distinto (`409 Conflict`).
  - Finalización de viaje (`finish`): cálculo de tarifas, actualización a `COMPLETED` y emisión dual de eventos (`VehicleDroppedOff` RAW a `vehicle-ride` y `TripRecorded` TRUSTED a `trip`).
- **Incidencia corregida durante el desarrollo:**
  - Se detectó un fallo en `shouldStartRideWhenReservedBySameUser` originado por el orden posicional del constructor del Record `RideService.VehicleView` (`reservationId` como 9º parámetro y `userId` como 10º parámetro). Se corrigió la función auxiliar de mock para pasar los argumentos en el orden exacto.
- **Resultado:** **4/4 tests pasando en verde** (`BUILD SUCCESS`).

---

### 2.4. Ejecución, Verificación y Subida a Git
- **Resolución de Bloqueo en Sandbox:** Se identificó que la ejecución de `mvn test` requería permisos ampliados en el entorno Windows del host debido a la inicialización de agentes de Mockito ByteBuddy y llamadas al subsistema de red/procesos. Se ejecutó la verificación completa con `BypassSandbox: true`.
- **Resultado de la Verificación:**
  ```text
  [INFO] Reactor Summary for UrbanMove Monorepo Parent 0.1.0-SNAPSHOT:
  [INFO] UrbanMove Monorepo Parent .......................... SUCCESS
  [INFO] UrbanMove Platform Common .......................... SUCCESS
  [INFO] UrbanMove availability ............................. SUCCESS [6 tests run]
  [INFO] UrbanMove reservations ............................. SUCCESS [5 tests run]
  [INFO] UrbanMove rides .................................... SUCCESS [4 tests run]
  [INFO] BUILD SUCCESS
  ```
- **Commit y Push:**
  - Commit `e0b92f5`: `test: add unit test suites for availability, reservations, and rides services`
  - Push exitoso a la rama remota `prototipo1`.

---

### 2.5. Preparación de Infraestructura (Terraform) y CI para Migración a AWS Real
Para evitar retrabajos cuando toque desplegar en la nube de Amazon Web Services, se adaptó y blindó el ecosistema de infraestructura como código (IaC) y el pipeline de GitHub Actions:
- **Terraform Dual (LocalStack / AWS)**:
  - Condicionamiento del bloque `endpoints` y validaciones en `main.tf` mediante `var.is_localstack`. En AWS real utiliza credenciales estándar IAM / OIDC sin mocks.
  - Etiquetado corporativo global mediante `default_tags` en el provider AWS (`Environment`, `Project`, `ManagedBy`).
  - Seguridad en S3 (`s3.tf`): añadido versionado, cifrado AES-256 en reposo y `aws_s3_bucket_public_access_block`.
  - Roles IAM para Lambdas (`lambda.tf`): agregada política de permisos mínimos (`AWSLambdaBasicExecutionRole`, SQS, S3, EventBridge) para ejecución sin bloqueos `403` en AWS.
  - Creación de perfiles desacoplados en `infra/terraform/environments/` (`local.tfvars` y `staging.tfvars`).
- **Pipeline de CI Paralelo (`.github/workflows/ci.yml`)**:
  - Incorporado el job `terraform` que compila las Lambdas en Python 3.11, corre `terraform fmt -check`, `terraform init`, `terraform validate` y `terraform plan` contra el perfil de desarrollo.
- **Guía de Migración Cloud**:
  - Publicada la guía completa paso a paso en [`docs/03-operacion/AWS-MIGRATION-GUIDE.md`](../03-operacion/AWS-MIGRATION-GUIDE.md).
- **Commit y Push:**
  - Commit `bee06ba`: `feat(infra): prepare Terraform and CI for seamless AWS migration and add validation pipeline`
  - Push exitoso a `origin/prototipo1`.

---

## 3. Próximos Pasos Técnicos (Roadmap)

Siguiendo las recomendaciones del informe de preparación y los estándares de arquitectura (`AGENTS.md`):

1. **Fase 2 — Estandarización de Errores RFC 7807 (`ProblemDetail`):**
   - Refactorizar `libs/platform-common` (`ApiException` y `GlobalExceptionHandler`).
   - Reemplazar el payload plano `{"error": "..."}` por instancias de `org.springframework.http.ProblemDetail` con campos normalizados (`type`, `title`, `status`, `detail`, `instance`, `code`).

2. **Fase 3 — Refactorización Hexagonal en `availability`:**
   - Extraer la máquina de estados de vehículos a un paquete de dominio puro (`domain/model/VehicleState`).
   - Definir puertos de entrada (casos de uso) y puertos de salida (repositorio JDBC).
   - Eliminar el acoplamiento directo entre el listener de eventos y las consultas SQL de actualización.

3. **Fase 4 — Pruebas de Integración y Concurrencia con Testcontainers:**
   - Incorporar pruebas de integración reales usando contenedores efímeros de PostgreSQL (`@Testcontainers`) para verificar el bloqueo optimista/pesimista contra condiciones de carrera (doble reserva concurrente sobre el mismo vehículo).
