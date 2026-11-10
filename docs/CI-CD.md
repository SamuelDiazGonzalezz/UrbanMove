# CI/CD y calidad

> El enunciado exige una práctica DevOps visible y reproducible: pruebas automáticas,
> construcción de imágenes, análisis estático, empaquetado de Lambdas, migraciones y
> evidencias de ejecución.

---

## 1. Estrategia

- **Repositorio único (monorepo)** con carpetas independientes por servicio.
- **Ramas cortas** y PRs pequeños; `main` siempre verde.
- **Contratos primero:** OpenAPI y JSON Schema antes de implementar.
- **Pipeline por capas:** rápido en cada PR, completo en `main`, despliegue manual o
  automático según el entorno.
- **Local y CI usan los mismos comandos** (`docker compose`, `mvnw`, scripts).
- **Cero secretos** en el repositorio.

---

## 2. Workflows previstos

| Workflow | Disparador | Qué hace |
|---|---|---|
| `ci.yml` | PR y push a `main` | Compila, testea, formatea, analiza y valida contratos |
| `integration.yml` | PR y `main` | Levanta Compose + LocalStack + Keycloak y ejecuta E2E |
| `cd.yml` | Push a `main` o tag | Construye imágenes, publica en GHCR/ECR y despliega |
| `infra.yml` | Cambios en `infra/` | `terraform fmt`, `validate`, `plan` |
| `security.yml` | Programado y push | CodeQL, Trivy, SBOM y Dependabot |

### 2.1. `ci.yml`

Pasos:

1. Checkout.
2. JDK 21 (Temurin) + caché Maven.
3. Detectar módulos cambiados (`dorny/paths-filter` o matriz manual).
4. `./mvnw -pl ... verify`.
5. Spotless/Checkstyle/SpotBugs.
6. JaCoCo (umbral mínimo en dominio).
7. Trivy (filesystem).
8. CodeQL (Java/Kotlin).
9. Validar OpenAPI (Spectral) y JSON Schema.
10. Construir imágenes Docker sin publicar.
11. Empaquetar Lambdas.
12. Publicar informes y artefactos.

### 2.2. `integration.yml`

1. Levantar PostgreSQL, LocalStack y Keycloak como servicios del job.
2. `docker compose up -d`.
3. Ejecutar migraciones y seed.
4. Ejecutar tests E2E (REST Assured o Postman/Newman).
5. Ejecutar los flujos 1, 2 y 5 en modo smoke.
6. Subir logs, capturas y resultados.

### 2.3. `cd.yml`

Local/EC2:

1. Construir imágenes con Jib o Docker.
2. Publicar en GHCR.
3. Desplegar por SSH en la EC2 de demo (`docker compose pull && docker compose up -d`).
4. Smoke test contra el entorno desplegado.
5. Publicar evidencia.

AWS real (cuando exista):

1. OIDC a AWS (`id-token: write`).
2. Push a ECR.
3. Actualizar ECS o EC2.
4. Smoke test y rollback documentado.

### 2.4. `infra.yml`

- `terraform fmt -check`
- `terraform validate`
- `tflint`
- `checkov` (opcional)
- `terraform plan` en PR
- `terraform apply` solo con aprobación manual

### 2.5. `security.yml`

- CodeQL semanal.
- Trivy de imágenes y filesystem.
- SBOM con CycloneDX.
- Dependabot para Maven, npm y GitHub Actions.
- Secret scanning y push protection.

---

## 3. Quality gates

| Gate | Herramienta | Umbral |
|---|---|---|
| Formato | Spotless | Sin diferencias |
| Estilo | Checkstyle | Sin errores |
| Bugs | SpotBugs | Sin alta severidad |
| Cobertura | JaCoCo | ≥ 70% en dominio, ≥ 50% global |
| Vulnerabilidades | Trivy | Sin CRITICAL/HIGH sin justificar |
| Contratos | Spectral + JSON Schema | Válidos |
| Tests | JUnit 5 + Testcontainers | Todos verdes |
| E2E | Postman/Newman o REST Assured | Flujos críticos verdes |

---

## 4. Pirámide de pruebas

| Nivel | Herramienta | Qué cubre |
|---|---|---|
| Unitario | JUnit 5 + AssertJ + Mockito | Dominio y casos de uso |
| Slice | `@WebMvcTest` | Controladores y validación |
| Integración | Testcontainers | PostgreSQL, LocalStack, Keycloak |
| Contrato | JSON Schema + OpenAPI | Productores y consumidores |
| E2E | Postman/Newman o REST Assured | Flujos completos |
| Carga | Locust | Reservas y viajes |

Reglas:

- Nada de `Thread.sleep()`; usar Awaitility.
- Nada de H2: PostgreSQL real con Testcontainers.
- Cada servicio tiene sus propios tests.
- Los tests E2E viven en el repositorio y se ejecutan en CI.

---

## 5. Evidencias para la entrega

El PDF y la defensa deben enseñar:

- Badge o captura de GitHub Actions verde.
- Log de un pipeline completo.
- Informe de tests y cobertura.
- Imagen publicada en GHCR/ECR con digest.
- `terraform plan` y `apply` (si aplica).
- Logs estructurados con `correlationId`.
- Captura de Grafana.
- Consulta de un data mart.
- Mensaje en DLQ y reprocesamiento.
- Flujo de fallo controlado.

---

## 6. Gestión de secretos

- `.env` ignorado; `.env.example` versionado con placeholders.
- LocalStack usa `test`/`test` (no son credenciales reales).
- Keycloak local con credenciales de desarrollo marcadas.
- AWS: Secrets Manager o SSM Parameter Store.
- GitHub Actions → AWS con OIDC; nunca `AWS_ACCESS_KEY_ID` de larga duración.
- Si se usa SSH para desplegar, la clave va en GitHub Secrets, no en el repo.
- Escaneo de secretos antes de cada push.
- Prohibido imprimir tokens, contraseñas o cadenas de conexión en logs.

---

## 7. Despliegue y rollback

- **Local:** `docker compose up -d`; rollback = volver a la imagen anterior.
- **EC2 demo:** `docker compose pull && docker compose up -d`; rollback = tag anterior.
- **ECS (ampliación):** despliegue rolling o blue/green simulado; rollback = revisión
  anterior del task definition.
- **Terraform:** `terraform plan` revisado; `apply` con aprobación; `destroy` al
  terminar la demo.
- Cada despliegue debe tener un smoke test y un registro de la versión desplegada.

---

## 8. Convenciones

- Commits: `feat:`, `fix:`, `docs:`, `test:`, `chore:`, `refactor:`.
- PR: descripción, issue, evidencias y checklist.
- Ramas: `feat/<tema>`, `fix/<tema>`, `docs/<tema>`.
- `main` protegida: PR obligatorio, CI verde y al menos una revisión.
- No se mergea código sin tests ni contratos actualizados.

---

## 9. Checklist de la Fase 0

- [ ] `ci.yml` con build + tests + formato.
- [ ] `integration.yml` con Compose + LocalStack + Keycloak.
- [ ] `cd.yml` publicando imágenes en GHCR.
- [ ] `infra.yml` con Terraform validate/plan.
- [ ] `security.yml` con CodeQL/Trivy.
- [ ] Branch protection en `main`.
- [ ] Dependabot activo.
- [ ] Primer pipeline verde con evidencia.
