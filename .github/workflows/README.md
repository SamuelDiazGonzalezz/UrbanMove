# GitHub Actions

Los pipelines se implementarán en la Fase 0 y se ampliarán con cada servicio.

| Workflow | Disparador | Propósito |
|---|---|---|
| `ci.yml` | Pull request y push a `main` | Compilar, testear, analizar y validar contratos |
| `integration.yml` | Pull request y `main` | Levantar Compose + LocalStack + Keycloak y ejecutar E2E |
| `cd.yml` | Push a `main` o tag | Construir imágenes, publicar en GHCR/ECR y desplegar |
| `infra.yml` | Cambios en `infra/` | `terraform fmt`, `validate`, `plan` |
| `security.yml` | Programado y push | CodeQL, Trivy, SBOM y Dependabot |

## Reglas

- Cero secretos en el repositorio.
- AWS real: OIDC (`id-token: write`) y roles con permisos mínimos.
- LocalStack: credenciales `test`/`test` solo en CI.
- Cada workflow debe publicar artefactos de evidencia (informes, logs, capturas).
- La rama `main` debe estar protegida y requerir PR.
