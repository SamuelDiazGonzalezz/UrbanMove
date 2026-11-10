# UrbanMove 🚲🛴

Sistema distribuido de movilidad urbana (bicicletas y patinetes eléctricos) para la asignatura **Computación en la Nube** del Grado en Ingeniería Informática (ULPGC).

> **Estado:** Fase 0 — diseño y planificación.
> Este repositorio contiene **únicamente documentación**. Todavía no hay código, infraestructura ni pipelines implementados. El objetivo es que el equipo revise y valide las decisiones antes de empezar a construir.

---

## 🎯 Objetivo

Diseñar, implementar y desplegar una arquitectura académica que demuestre:

- 10 microservicios atómicos con responsabilidades y datos propios.
- 3 interfaces diferenciadas: usuario, mantenimiento/operaciones y dashboard.
- Comunicación asíncrona por eventos.
- 3 funciones Lambda conectadas a eventos reales.
- Data Lake con capas raw / trusted / curated.
- Al menos 4 data marts y dashboard operativo.
- Generación de órdenes de recolocación a partir de la demanda.
- Tolerancia a fallos, escalabilidad y observabilidad.
- CI/CD reproducible con GitHub Actions.
- Infraestructura como código.

---

## 📚 Documentación

- [Índice de documentación](docs/README.md): estructura de `docs/`, orden de lectura y cómo actualizarla.
- [Plan maestro](docs/00-gestion/PLAN.md): alcance, requisitos, arquitectura, roadmap, equipo de 3, checklist, demo y rúbrica.
- [Reunión de arranque](docs/00-gestion/KICKOFF.md): agenda, decisiones clave, reparto, checkboxes y acta para la primera reunión del equipo.
- [Arquitectura](docs/01-arquitectura/ARCHITECTURE.md): C4, catálogo de servicios, propiedad de datos, estados y despliegue.
- [Arquitectura de datos](docs/01-arquitectura/DATA-ARCHITECTURE.md): S3, PostgreSQL, DynamoDB, Parquet, eventos, inmutabilidad, dos instancias RDS y plan B.
- [Entorno local](docs/02-desarrollo/LOCAL-DEV.md): herramientas, versiones, instalación, arranque de Docker, TDD y checklist de incorporación.
- [Estándares de código](docs/02-desarrollo/CODE-STANDARDS.md): nomenclatura, legibilidad, SOLID, Ley de Deméter, inmutabilidad y reglas de arquitectura hexagonal.
- [Pruebas](docs/02-desarrollo/TESTING.md): pirámide, Testcontainers, LocalStack, idempotencia, contratos y cobertura.
- [Contenedores](docs/02-desarrollo/CONTAINERS.md): Dockerfile multi-stage, JVM, probes, Compose y seguridad de imagen.
- [Eventos](docs/01-arquitectura/EVENTS.md): sobre de evento, catálogo, outbox, idempotencia y DLQ.
- [CI/CD](docs/03-operacion/CI-CD.md): pipelines, quality gates, evidencias y gestión de secretos.
- [ADRs](docs/01-arquitectura/adr/): decisiones arquitectónicas iniciales.
- [Enunciado original](docs/04-enunciado/urbanmove.pdf).

---

## 🧭 Decisiones que el equipo debe revisar

Antes de implementar nada, conviene validar estas decisiones:

1. **Local-first con Docker Compose + LocalStack**, dejando AWS para una fase posterior.
2. **PostgreSQL por microservicio** como base operacional; S3 solo para el Data Lake.
3. **Dos instancias RDS** (operacional y analítica) como objetivo, con **plan B de una
   sola instancia** si AWS o el crédito lo complican. Ver
   [`docs/01-arquitectura/DATA-ARCHITECTURE.md`](docs/01-arquitectura/DATA-ARCHITECTURE.md).
4. **Kappa + Medallón** para la capa analítica.
5. **Keycloak en local y Cognito en AWS** para el login.
6. **Swagger/Postman + Grafana** como interfaces iniciales, sin frontend propio.
7. **EventBridge + SQS + DLQ** como mecanismo de mensajería.
8. **Terraform + GitHub Actions** para infraestructura y CI/CD.

Cada decisión está justificada en su ADR correspondiente dentro de [`docs/01-arquitectura/adr/`](docs/01-arquitectura/adr/).

---

## 💻 Entorno local

Antes de instalar nada, ten en cuenta el hardware:

| Recurso | Mínimo | Recomendado |
|---|---|---|
| RAM | 8 GB | **16 GB** |
| CPU | 4 núcleos | 6-8 núcleos |
| Disco libre | 15 GB | 30 GB |

Con 16 GB se pueden levantar la infraestructura y los 13 microservicios a la vez. Con
8 GB hay que usar los perfiles de Docker Compose y no arrancar todo. La guía completa de
herramientas, versiones, instalación y orden de arranque está en
[`docs/02-desarrollo/LOCAL-DEV.md`](docs/02-desarrollo/LOCAL-DEV.md).

---

## 🗂️ Estructura prevista (aún no creada)

Cuando el equipo apruebe el plan, la Fase 0 creará esta estructura:

```text
urbanmobility/
├── services/          # Microservicios Spring Boot independientes
├── lambdas/           # Funciones serverless
├── infra/             # Terraform y configuración de LocalStack
├── contracts/         # OpenAPI, AsyncAPI y JSON Schema
├── clients/           # Swagger/Postman y Grafana
├── docs/              # Documentación organizada por carpetas (ver docs/README.md)
├── scripts/           # Seed, demo y utilidades
├── .github/workflows/ # CI/CD
├── docker-compose.yml # Plataforma local
└── pom.xml            # POM padre del monorepo
```

---

## 🚦 Estado

- [x] Analizar el enunciado completo.
- [x] Identificar los mínimos obligatorios y la rúbrica.
- [x] Documentar la arquitectura y las decisiones iniciales.
- [x] Reiniciar el repositorio y dejar solo la documentación.
- [ ] Revisar y validar las decisiones con el equipo.
- [ ] Empezar la Fase 0: plataforma local, POM padre, Keycloak, LocalStack y CI.

---

## 👥 Equipo

Proyecto de 3 personas. El reparto propuesto está en [`docs/00-gestion/PLAN.md`](docs/00-gestion/PLAN.md#20-reparto-del-equipo).

---

## 🔐 Seguridad

- No se versionan credenciales ni secretos.
- Cuando exista código, `.env` estará ignorado y solo se versionará `.env.example`.
- En AWS se usarán roles IAM y OIDC para GitHub Actions.
- Las credenciales de LocalStack serán de desarrollo (`test`/`test`).

---

## 📄 Licencia

Proyecto académico. Uso educativo.
