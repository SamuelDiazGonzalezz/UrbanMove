# Session Handoff — UrbanMove (`prototipo1`)

> **Rama:** `prototipo1`  
> **Fecha:** 2026-11-26  
> **Último commit previo:** `bb26377`  
> **Objetivo:** Dejar documentado el estado actual de pruebas, CI/CD, Terraform y código Java, junto con la hoja de ruta exacta para continuar la preparación hacia AWS real.

---

## 1. Estado Actual del Repositorio

### 1.1. Cobertura de Pruebas (13/13 Microservicios)
Todos los microservicios de `prototipo1` cuentan ahora con suites de pruebas unitarias (JUnit 5 + Mockito + AssertJ) verificadas en verde:
- `availability` (`AvailabilityServiceTest` — 6 tests)
- `reservations` (`ReservationServiceTest` — 5 tests)
- `rides` (`RideServiceTest` — 4 tests)
- `zones` (`ZoneServiceTest` — 3 tests + `ZoneServiceIntegrationTest` con **Testcontainers PostgreSQL** y `@Testcontainers(disabledWithoutDocker = true)`)
- `inventory` (`InventoryServiceTest` — 3 tests)
- `maintenance` (`MaintenanceServiceTest` — 2 tests)
- `relocation` (`RelocationServiceTest` — 2 tests)
- `telemetry` (`TelemetryControllerTest` — 2 tests)
- `user-profiles` (`ProfileControllerTest` — 2 tests)
- `history` (`HistoryServiceTest` — 2 tests)
- `identity` (`IdentityServiceTest` — 1 test)
- `incidents` (`IncidentControllerTest` — 1 test)
- `analytics` (`AnalyticsControllerTest` — 1 test)

### 1.2. Estándares de Código y Preparación Cloud
- **RFC 7807 (`ProblemDetail`)**: [`GlobalExceptionHandler.java`](../../libs/platform-common/src/main/java/com/urbanmove/common/web/GlobalExceptionHandler.java) migrado a `org.springframework.http.ProblemDetail` con `type`, `title`, `status`, `detail`, `code` y `correlationId`.
- **Ley de Deméter (Tell, Don't Ask)**: [`AvailabilityService.Vehicle.toPublicView()`](../../services/availability/src/main/java/com/urbanmove/availability/AvailabilityService.java) encapsula la proyección pública usada por [`AvailabilityController`](../../services/availability/src/main/java/com/urbanmove/availability/AvailabilityController.java).
- **URLs entre servicios externalizadas**:
  - `InventoryService` acepta `ZONES_URL` (`@Value("${ZONES_URL:http://zones:8080}")`).
  - `MaintenanceService` acepta `AVAILABILITY_URL` (`@Value("${AVAILABILITY_URL:http://availability:8080}")`).

### 1.3. Terraform y GitHub Actions (CI)
- **Terraform dual LocalStack / AWS** (`infra/terraform/`):
  - `main.tf` con `endpoints` condicionales (`var.is_localstack`), `default_tags` y plantilla de backend remoto S3 + DynamoDB.
  - `s3.tf` con versionado, cifrado AES256, `Public Access Block` y `force_destroy = var.is_localstack`.
  - `lambda.tf` con roles y políticas IAM mínimas (`AWSLambdaBasicExecutionRole`, SQS, S3, EventBridge).
  - Perfiles en `infra/terraform/environments/local.tfvars` y `staging.tfvars` (desbloqueados en `.gitignore`).
- **GitHub Actions (`.github/workflows/ci.yml`)**:
  - Job `test`: compila con Java 21 Temurin y ejecuta `mvn -B -ntp clean test` (incluyendo Testcontainers en `ubuntu-latest`).
  - Job `terraform`: empaqueta las Lambdas en Python 3.11 y ejecuta `terraform fmt -check`, `terraform init`, `terraform validate` y `terraform plan -var-file=environments/local.tfvars`.
- **Guía de Migración**: [`docs/03-operaciones/AWS-MIGRATION-GUIDE.md`](../03-operaciones/AWS-MIGRATION-GUIDE.md).

---

## 2. Qué hacer en los próximos pasos (LocalStack / Código → AWS)

### Prioridad 1: Parametrizar `lambda_env` en Terraform (`infra/terraform/lambda.tf`)
- **Problema actual:** `RESERVATIONS_URL = "http://reservations:8080"`, `MAINTENANCE_URL = "http://maintenance:8080"` y `DB_HOST = "postgres"` están fijos para Docker Compose.
- **Acción:** Convertirlos en variables (`var.reservations_url`, `var.maintenance_url`, `var.db_host`), manteniendo los valores actuales en `environments/local.tfvars` y preparando los endpoints de AWS (RDS/Aurora y ALB/Cloud Map) en `environments/staging.tfvars`.

### Prioridad 2: Tolerancia a fallos con Resilience4j
- **Acción:** Añadir `resilience4j-spring-boot3` en `libs/platform-common` y proteger con `@CircuitBreaker` y `@Retry` las llamadas HTTP salientes (`InventoryService` → `zones`, `MaintenanceService` → `availability`, `IdentityService` → `KeycloakClient`).

### Prioridad 3: Refactorización Hexagonal en `services/availability`
- **Acción:** Extraer la máquina de estados de vehículos a un paquete `domain` puro, separado de `JdbcTemplate`, siguiendo el patrón de `core-fleet` (`domain`, `ports.in`, `ports.out`, `adapters`).

### Prioridad 4: Ampliar Pruebas de Integración con Testcontainers
- **Acción:** Replicar el patrón de [`ZoneServiceIntegrationTest`](../../services/zones/src/test/java/com/urbanmove/zones/ZoneServiceIntegrationTest.java) (`@Testcontainers(disabledWithoutDocker = true)`) en `services/reservations` y `services/rides` para validar bloqueos y concurrencia real en PostgreSQL.

### Prioridad 5: Módulos de Cómputo y Datos para AWS Real
- **Acción (cuando toque desplegar en AWS):**
  1. Descomentar el `backend "s3"` en `infra/terraform/main.tf`.
  2. Añadir módulos Terraform para **RDS PostgreSQL / Aurora**, **ECR** (imágenes Docker de los 13 servicios) y **ECS Fargate + ALB + Cloud Map** (con `AWS_ENDPOINT_URL` vacío).
