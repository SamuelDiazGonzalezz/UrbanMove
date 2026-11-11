# Documentación de UrbanMove

> **Esta carpeta es la fuente de verdad del diseño.** El repositorio no contiene código
> todavía: primero se valida el plan y después se implementa la Fase 0.
> **Última reestructuración:** noviembre de 2026.

---

## 1. Estructura

```text
docs/
├── README.md                     este índice
├── 00-gestion/
│   ├── ACTION-PLAN.md            qué se hace cada semana, en orden
│   ├── GITHUB-WORKFLOW.md        uso mínimo de issues y etiquetas
│   ├── KICKOFF.md                reunión de arranque: decisiones, reparto y acta
│   ├── KICKOFF.pdf               copia para imprimir
│   └── PLAN.md                   plan maestro, fases, riesgos y rúbrica
├── 01-arquitectura/
│   ├── ARCHITECTURE.md           C4, servicios, estados y despliegue
│   ├── DATA-ARCHITECTURE.md      PostgreSQL, S3, DynamoDB, marts y RDS
│   ├── DATA-DICTIONARY.md        atributos de todas las tablas (documento vivo)
│   ├── DATA-MODEL.md             relaciones entre tablas y grano de cada capa
│   ├── EVENT-SOURCING.md         event store, proyecciones y RAW/TRUSTED/CURATED
│   ├── EVENTS.md                 sobre de evento, catálogo por capa y DLQ
│   ├── OLTP-OLAP.md              cargas transaccional y analítica, local y AWS
│   ├── SERVICE-CLASSIFICATION.md qué servicio es OLTP y cuál OLAP
│   ├── SERVICE-ROADMAP.md        orden, dependencias y recortes de servicios
│   └── adr/                      decisiones arquitectónicas (0001-0007)
├── 02-desarrollo/
│   ├── LOCAL-DEV.md              herramientas, versiones y arranque local
│   ├── CODE-STANDARDS.md         nomenclatura, legibilidad, SOLID e inmutabilidad
│   ├── TESTING.md                pirámide, Testcontainers y contratos
│   └── CONTAINERS.md             Dockerfile, JVM, probes y Compose
├── 03-operacion/
│   └── CI-CD.md                  pipelines, quality gates y secretos
└── 04-enunciado/
    └── urbanmove.pdf             enunciado original
```

Las carpetas van numeradas para que el orden de lectura sea evidente en GitHub y en
cualquier explorador de archivos. Los nombres de documento se mantienen en mayúsculas
(`ARCHITECTURE.md`) y los ADR en `kebab-case` con número (`0006-dos-instancias-rds.md`).

---

## 2. Qué contiene cada documento

| Documento | Para qué sirve | Cuándo se lee |
|---|---|---|
| [ACTION-PLAN.md](00-gestion/ACTION-PLAN.md) | Qué se hace cada semana, en qué orden y qué entregable lo cierra | Al empezar la semana |
| [GITHUB-WORKFLOW.md](00-gestion/GITHUB-WORKFLOW.md) | Cuándo abrir una issue, etiquetas mínimas y comandos `gh` | Antes de crear la primera tarea |
| [KICKOFF.md](00-gestion/KICKOFF.md) | Cerrar decisiones, reparto y plan de la semana 1 | Antes y durante la primera reunión |
| [PLAN.md](00-gestion/PLAN.md) | Alcance, fases, reparto, riesgos y demo | Al empezar y al planificar cada fase |
| [ARCHITECTURE.md](01-arquitectura/ARCHITECTURE.md) | Servicios, comunicación, estados y despliegue | Antes de diseñar cualquier servicio |
| [SERVICE-ROADMAP.md](01-arquitectura/SERVICE-ROADMAP.md) | Qué servicio va antes, de qué depende y qué se recorta | Al planificar cada semana |
| [DATA-ARCHITECTURE.md](01-arquitectura/DATA-ARCHITECTURE.md) | Dónde vive cada dato y por qué | Antes de tocar persistencia o analítica |
| [DATA-MODEL.md](01-arquitectura/DATA-MODEL.md) | Relaciones entre tablas y grano de cada capa | Antes de crear una tabla o un join |
| [DATA-DICTIONARY.md](01-arquitectura/DATA-DICTIONARY.md) | Atributos, tipos y nulabilidad de cada tabla | Al escribir una migración Flyway |
| [EVENT-SOURCING.md](01-arquitectura/EVENT-SOURCING.md) | Event store, proyecciones y capas RAW/TRUSTED/CURATED | Antes de modelar estado o un agregado |
| [OLTP-OLAP.md](01-arquitectura/OLTP-OLAP.md) | Separación de carga transaccional y analítica, en local y en AWS | Antes de diseñar persistencia, marts o paneles |
| [SERVICE-CLASSIFICATION.md](01-arquitectura/SERVICE-CLASSIFICATION.md) | Qué servicio es OLTP y cuál es OLAP | Al explicar la arquitectura o el reparto |
| [EVENTS.md](01-arquitectura/EVENTS.md) | Contrato de eventos, publicación, idempotencia y DLQ | Antes de publicar o consumir un evento |
| [LOCAL-DEV.md](02-desarrollo/LOCAL-DEV.md) | Instalar y arrancar el entorno | El primer día de cada integrante |
| [CODE-STANDARDS.md](02-desarrollo/CODE-STANDARDS.md) | Cómo se escribe y se revisa el código | Antes del primer PR y en cada revisión |
| [TESTING.md](02-desarrollo/TESTING.md) | Qué se prueba, con qué y con qué umbral | Antes de escribir el primer test |
| [CONTAINERS.md](02-desarrollo/CONTAINERS.md) | Cómo se empaqueta y se ejecuta un servicio | Antes de crear el primer Dockerfile |
| [CI-CD.md](03-operacion/CI-CD.md) | Pipelines, gates y gestión de secretos | Antes de configurar GitHub Actions |
| [ADRs](01-arquitectura/adr/) | Por qué se tomó cada decisión | Al dudar de una decisión existente |
| [Enunciado](04-enunciado/urbanmove.pdf) | Requisitos originales de la asignatura | Al validar alcance y rúbrica |

---

## 3. Por dónde empezar

### 3.1. Si acabas de entrar en el equipo

1. [KICKOFF.md](00-gestion/KICKOFF.md) — contexto, decisiones y reparto.
2. [ACTION-PLAN.md](00-gestion/ACTION-PLAN.md) — qué toca esta semana y en qué orden.
3. [PLAN.md](00-gestion/PLAN.md) — alcance, fases y rúbrica.
4. [ARCHITECTURE.md](01-arquitectura/ARCHITECTURE.md) — cómo encajan los servicios.
5. [LOCAL-DEV.md](02-desarrollo/LOCAL-DEV.md) — cómo levantar el entorno.

### 3.2. Antes de escribir código

1. [CODE-STANDARDS.md](02-desarrollo/CODE-STANDARDS.md).
2. [TESTING.md](02-desarrollo/TESTING.md).
3. [CONTAINERS.md](02-desarrollo/CONTAINERS.md) si vas a empaquetar.

### 3.3. Antes de tocar datos o eventos

1. [EVENT-SOURCING.md](01-arquitectura/EVENT-SOURCING.md) y
   [ADR 0007](01-arquitectura/adr/0007-event-sourcing-raw-trusted-curated.md).
2. [DATA-ARCHITECTURE.md](01-arquitectura/DATA-ARCHITECTURE.md).
3. [OLTP-OLAP.md](01-arquitectura/OLTP-OLAP.md) y
   [SERVICE-CLASSIFICATION.md](01-arquitectura/SERVICE-CLASSIFICATION.md).
4. [DATA-MODEL.md](01-arquitectura/DATA-MODEL.md).
5. [DATA-DICTIONARY.md](01-arquitectura/DATA-DICTIONARY.md).
6. [EVENTS.md](01-arquitectura/EVENTS.md).

### 3.4. Antes de desplegar

1. [CI-CD.md](03-operacion/CI-CD.md).
2. [CONTAINERS.md](02-desarrollo/CONTAINERS.md), sección de despliegue.

### 3.5. Antes de planificar la semana

1. [ACTION-PLAN.md](00-gestion/ACTION-PLAN.md) — qué toca, en qué orden y cuándo se
   considera hecho.
2. [SERVICE-ROADMAP.md](01-arquitectura/SERVICE-ROADMAP.md) — qué depende de qué y qué
   se puede recortar.
3. [GITHUB-WORKFLOW.md](00-gestion/GITHUB-WORKFLOW.md) — cómo se crea y se cierra una
   issue.

---

## 4. Cómo actualizar la documentación

### 4.1. Regla básica

El Markdown es la fuente de verdad. El único PDF versionado es el de la reunión de
arranque, y es una copia, no un original.

### 4.2. Editar

| Situación | Cómo |
|---|---|
| Corrección pequeña | GitHub → lápiz → **Commit changes** a `main` |
| Cambio de fondo | Rama `docs/<tema>`, PR y revisión de otro integrante |
| Varios cambios locales | Editar, `git add`, commit y push |
| Reunión | Una sola persona edita; el resto dicta |

Mensajes de commit:

```text
docs: update kickoff decisions
docs: explain two-instance rds plan
docs: add testing strategy
```

### 4.3. Casillas y tablas

- Tarea hecha: `- [ ]` → `- [x]`.
- Decisión cerrada: `☐` → `☑`.
- Hueco a rellenar: `________________`.
- No se borran decisiones descartadas: se marcan y se explica el motivo.

La guía detallada está en [KICKOFF.md](00-gestion/KICKOFF.md), sección 2.

### 4.4. Enlaces

- Siempre relativos al documento que los contiene:
  - dentro de la misma carpeta: `[PLAN.md](PLAN.md)`;
  - a otra carpeta: `[EVENTS.md](../01-arquitectura/EVENTS.md)`.
- Nunca enlaces absolutos a `github.com` para documentos del propio repositorio.
- Si se mueve un documento, se actualizan **todos** los enlaces y se comprueba que
  resuelven antes del PR.

### 4.5. Decisiones nuevas

Si un cambio afecta a más de un servicio, a la persistencia o al despliegue:

1. Se escribe un ADR nuevo en `01-arquitectura/adr/` con el siguiente número.
2. Se enlaza desde el documento afectado.
3. No se reescribe el ADR anterior: se marca como sustituido.
4. Se anota en [PLAN.md](00-gestion/PLAN.md), tabla de decisiones.

### 4.6. Secretos

Nunca se documentan credenciales reales, tokens, cadenas de conexión con contraseña ni
claves. Los ejemplos usan valores de desarrollo (`urbanmove_dev_only`) y `.env.example`.

---

## 5. Responsables de mantenimiento

| Documento | Responsable | Revisión |
|---|---|---|
| `ACTION-PLAN.md` | A | Todos al cerrar cada semana |
| `GITHUB-WORKFLOW.md` | A | Todos al cambiar el reparto |
| `KICKOFF.md` | A | Todos antes de la reunión |
| `PLAN.md` | A | Todos al cerrar cada fase |
| `ARCHITECTURE.md` | C | A y B al añadir un servicio |
| `SERVICE-ROADMAP.md` | A | B y C al cambiar dependencias |
| `DATA-ARCHITECTURE.md` | C | B al cambiar eventos |
| `DATA-MODEL.md` | C | B al cambiar eventos |
| `DATA-DICTIONARY.md` | C | B y A al cambiar una migración |
| `EVENT-SOURCING.md` | C | A y B al cambiar el modelo de persistencia |
| `OLTP-OLAP.md` | C | B al cambiar marts o eventos |
| `SERVICE-CLASSIFICATION.md` | C | A al explicar la arquitectura |
| `EVENTS.md` | B | A y C al añadir un evento |
| `LOCAL-DEV.md` | C | Todos al cambiar una versión |
| `CODE-STANDARDS.md` | A | Todos en cada PR |
| `TESTING.md` | A | B y C al añadir un nivel de prueba |
| `CONTAINERS.md` | B | C al cambiar el despliegue |
| `CI-CD.md` | C | B al añadir un gate |
| `ADR` | Quien propone | Los otros dos |

Los roles A, B y C se asignan a nombres reales en la sección 6 de
[KICKOFF.md](00-gestion/KICKOFF.md).

---

## 6. PDF de la reunión

- Archivo: [KICKOFF.pdf](00-gestion/KICKOFF.pdf).
- No se edita a mano.
- Se regenera con `pandoc` cuando cambie el Markdown y haga falta una copia impresa:

```bash
pandoc docs/00-gestion/KICKOFF.md \
  -o docs/00-gestion/KICKOFF.pdf \
  --pdf-engine=wkhtmltopdf
```

Si `pandoc` no está instalado, se imprime el Markdown desde GitHub o se pide al asistente
que regenere el PDF.

---

## 7. Enlaces rápidos

- [README del repositorio](../README.md)
- [Flujo de trabajo en GitHub](00-gestion/GITHUB-WORKFLOW.md)
- [Plan de acción](00-gestion/ACTION-PLAN.md)
- [Reunión de arranque](00-gestion/KICKOFF.md)
- [Plan maestro](00-gestion/PLAN.md)
- [Roadmap de servicios](01-arquitectura/SERVICE-ROADMAP.md)
- [Modelo de datos](01-arquitectura/DATA-MODEL.md)
- [Diccionario de datos](01-arquitectura/DATA-DICTIONARY.md)
- [Event sourcing](01-arquitectura/EVENT-SOURCING.md)
- [Catálogo de eventos](01-arquitectura/EVENTS.md)
- [OLTP y OLAP](01-arquitectura/OLTP-OLAP.md)
- [Clasificación OLTP/OLAP](01-arquitectura/SERVICE-CLASSIFICATION.md)
- [Decisiones arquitectónicas](01-arquitectura/adr/)
