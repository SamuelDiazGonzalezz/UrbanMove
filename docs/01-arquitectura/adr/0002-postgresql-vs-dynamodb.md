# 0002. PostgreSQL como base de datos operacional

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Decisores:** Equipo UrbanMove

## Contexto

El sistema necesita persistir usuarios, perfiles, zonas, activos, disponibilidad,
reservas, viajes, incidencias, órdenes de mantenimiento, órdenes de recolocación e
historial. Además, el Data Lake necesita almacenar eventos y los data marts necesitan
consultas agregadas.

Existe una confusión habitual: **S3 no es una base de datos operacional**. S3 es
almacenamiento de objetos para el Data Lake; no ofrece transacciones, restricciones de
unicidad ni consultas relacionales.

El enunciado menciona DynamoDB como ampliación valorable, pero no lo exige.

## Decisión

- **PostgreSQL 16** como base de datos operacional de todos los microservicios.
- Una **base de datos independiente por servicio** (por ejemplo `identity_db`,
  `reservations_db`, `rides_db`).
- En local, una sola instancia de PostgreSQL con varias bases de datos para ahorrar
  recursos. En AWS, una instancia RDS con varias bases de datos o instancias separadas
  si el presupuesto lo permite.
- **Flyway** para migraciones versionadas.
- **S3** exclusivamente para el Data Lake (raw/trusted/curated).
- **PostgreSQL analytics** (o esquema `analytics`) para materializar los data marts que
  consume el dashboard.
- **DynamoDB** queda como ampliación opcional, no como parte del núcleo.

### 2.1. Esta decisión no depende del entorno

La comparación **no es local frente a nube**. Es qué motor se usa para los datos
operacionales. El motor es el mismo en los dos entornos; solo cambia quién lo aloja.

| Entorno | Datos operacionales | Data Lake | DynamoDB |
|---|---|---|---|
| **Local** | Contenedor `postgres:16` con las 14 bases | LocalStack (S3 emulado) | No se usa; si se probara, LocalStack |
| **AWS** | RDS PostgreSQL (dos instancias o plan B) | S3 real | Solo ampliación opcional de telemetría |

Consecuencia práctica: el código no cambia entre local y AWS. Solo cambia la variable de
conexión (`DB_HOST`, `DB_URL`). Por eso la decisión se toma una vez y vale para los dos
entornos.

Lo que sí es una decisión de nube es **dónde se aloja PostgreSQL** (ADR 0006: dos
instancias RDS con plan B) y **cuándo se despliega** (ADR 0001: local primero, AWS
después).

## Alternativas consideradas

### A. DynamoDB para todo

- **Ventaja:** escalado horizontal y latencia muy baja.
- **Inconveniente:** modelar reservas, disponibilidad y consultas agregadas exige
  diseñar access patterns muy concretos; las transacciones y restricciones son más
  limitadas; añade un segundo paradigma y más configuración local.
- **Descartada para el núcleo.**

### B. DynamoDB solo para disponibilidad o telemetría

- **Ventaja:** demuestra el servicio y encaja con cargas de escritura alta.
- **Inconveniente:** duplica la complejidad de persistencia y no aporta al mínimo.
- **Aplazada como ampliación.**

### C. MySQL

- **Ventaja:** también relacional y conocido.
- **Inconveniente:** PostgreSQL tiene mejor soporte de tipos avanzados, JSONB,
  extensiones y herramientas analíticas.
- **Descartada.**

### D. MongoDB

- **Ventaja:** flexibilidad de esquema.
- **Inconveniente:** el dominio tiene relaciones y restricciones claras; no aporta.
- **Descartada.**

## Consecuencias

- Las reservas y la disponibilidad pueden usar transacciones, restricciones únicas y
  bloqueo optimista para evitar dobles reservas.
- Los data marts se pueden construir con SQL y consultar rápido desde Grafana.
- El equipo trabaja con una tecnología conocida y bien soportada por Spring Boot,
  Flyway y Testcontainers.
- Hay que gestionar migraciones y pools de conexión por servicio.
- Si se añade DynamoDB para telemetría, habrá que documentar el patrón de acceso y la
  consistencia eventual.

## Referencias

- Enunciado, secciones 4.1 y 8.
- [`docs/00-gestion/PLAN.md`](../../00-gestion/PLAN.md), sección 6.1.
- [`docs/01-arquitectura/ARCHITECTURE.md`](../../01-arquitectura/ARCHITECTURE.md), secciones 6 y 9.
