# UrbanMove 🚲🛴

Sistema distribuido de movilidad urbana (bicicletas y patinetes eléctricos) para la asignatura **Computación en la Nube** del Grado en Ingeniería Informática (ULPGC).

> **Estado:** Fase 0 — cimientos y documentación.
> El repositorio se ha reiniciado para empezar desde cero: la implementación anterior se eliminó y solo se conserva el enunciado como referencia.

---

## 🎯 Objetivo

Diseñar, implementar y desplegar una arquitectura académica que demuestre:

- Microservicios atómicos con responsabilidades claras.
- Comunicación asíncrona por eventos.
- Funciones Lambda para procesos automáticos.
- Data Lake con capas raw/trusted/curated.
- Data marts y dashboard operativo.
- Tolerancia a fallos, escalabilidad y observabilidad.
- CI/CD reproducible con GitHub Actions.
- Infraestructura como código.

---

## 🧱 Stack previsto

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Microservicios | Spring Boot 3.5.x |
| Persistencia | PostgreSQL 16 + Flyway |
| Identidad | Keycloak (local) / Cognito (AWS) |
| Mensajería | EventBridge + SQS + DLQ (LocalStack en local) |
| Serverless | AWS Lambda (Java/Python) |
| Data Lake | S3 (LocalStack/MinIO en local) |
| Analítica | PostgreSQL (marts) + Parquet + Grafana |
| Contenedores | Docker + Docker Compose |
| IaC | Terraform |
| CI/CD | GitHub Actions |
| Observabilidad | Actuator + Micrometer + Prometheus + Grafana |

---

## 📚 Documentación

- [Plan maestro](docs/PLAN.md): alcance, arquitectura, roadmap, equipo, checklist y demo.
- [Arquitectura](docs/ARCHITECTURE.md): C4, servicios, propiedad de datos, estados y despliegue.
- [Eventos](docs/EVENTS.md): sobre de evento, catálogo, outbox, idempotencia y DLQ.
- [CI/CD](docs/CI-CD.md): pipelines, quality gates, evidencias y secretos.
- [ADRs](docs/adr/): decisiones arquitectónicas.
- [Enunciado original](docs/urbanmove.pdf).

---

## 🗂️ Estructura del repositorio

```text
urbanmobility/
├── services/          # Microservicios Spring Boot independientes
├── lambdas/           # Funciones serverless
├── infra/             # Terraform y configuración de LocalStack
├── contracts/         # OpenAPI, AsyncAPI y JSON Schema
├── clients/           # App usuario, app operaciones y dashboard
├── docs/              # Documentación y enunciado
├── scripts/           # Seed, demo y utilidades
├── .github/workflows/ # CI/CD
├── docker-compose.yml # (Fase 0) plataforma local
└── pom.xml            # (Fase 0) POM padre del monorepo
```

---

## 🚀 Arranque local (previsto)

Requisitos: JDK 21, Docker Desktop, Git y, opcionalmente, Terraform y GitHub CLI.

```powershell
# 1. Copiar variables de entorno locales
Copy-Item .env.example .env

# 2. Levantar la plataforma local (cuando exista en Fase 0)
.\scripts\dev.ps1 up

# 3. Comprobar servicios
.\scripts\dev.ps1 ps
```

> Los comandos anteriores estarán disponibles cuando se complete la Fase 0. Hasta entonces, este README es la guía de intención y el plan está en [`docs/PLAN.md`](docs/PLAN.md).

---

## 👥 Equipo

Proyecto de 3 personas. El reparto propuesto está en [`docs/PLAN.md`](docs/PLAN.md#20-reparto-del-equipo).

---

## 🔐 Seguridad

- No se versionan credenciales ni secretos.
- `.env` está ignorado; solo se versiona `.env.example`.
- En AWS se usarán roles IAM y OIDC para GitHub Actions.
- Las credenciales de LocalStack son de desarrollo (`test`/`test`).

---

## 📄 Licencia

Proyecto académico. Uso educativo.
