# 0006. Dos instancias RDS: operacional y analítica

- **Estado:** Aceptada (con plan de contingencia)
- **Fecha:** 2026-11-10
- **Decisores:** Equipo UrbanMove

## Contexto

La arquitectura exige una base de datos por microservicio y una capa analítica con data
marts consumidos por Grafana. En la primera propuesta todo vivía en una sola instancia
PostgreSQL con varias bases de datos.

El análisis de escalabilidad identificó dos cuellos de botella:

1. Las escrituras concurrentes en reservas y disponibilidad.
2. Las consultas analíticas de Grafana compitiendo por CPU y conexiones con la
   operación.

Además, el equipo no sabe todavía si AWS Academy permitirá crear dos instancias RDS ni
si el crédito alcanzará para mantenerlas durante la demo.

## Decisión

Se adoptan **dos instancias RDS PostgreSQL**:

- `urbanmove-operational`: 12 bases operacionales, una por microservicio de negocio.
- `urbanmove-analytics`: `analytics_db`, con los data marts y un usuario de solo lectura
  para Grafana.

La separación es **física** (dos instancias) además de lógica (cada servicio con su base
y su usuario).

Se establece un **plan B oficial**: colapsar a una sola instancia con las 13 bases si la
integración en AWS o el crédito se complican. El cambio afecta únicamente a la variable
`DB_HOST` de `analytics` y de la Lambda `mart-builder`; no requiere modificar código ni
contratos.

## Alternativas consideradas

### A. Una sola instancia con 13 bases

- **Ventaja:** más barata, más simple de aprovisionar, un solo endpoint.
- **Inconveniente:** la analítica compite con la operación; menos aislamiento de
  seguridad; el fallo de la instancia afecta a todo.
- **No se descarta:** es el plan B si las condiciones de AWS lo exigen.

### B. Una instancia por microservicio (13 instancias)

- **Ventaja:** aislamiento físico total.
- **Inconveniente:** coste desproporcionado para un proyecto académico; 13 endpoints,
  13 backups, 13 configuraciones.
- **Descartada.**

### C. Aurora Serverless v2

- **Ventaja:** escala automáticamente y puede reducir coste en periodos sin uso.
- **Inconveniente:** más caro que RDS micro en uso bajo; menos disponible en cuentas de
  academia; más complejidad.
- **Descartada para el mínimo.**

### D. PostgreSQL en la EC2 de demo

- **Ventaja:** coste mínimo; todo en una máquina.
- **Inconveniente:** sin backups gestionados ni Multi-AZ; el alumno administra todo.
- **Aceptable solo como último recurso**, no como diseño por defecto.

## Consecuencias

- La carga analítica no afecta a reservas ni viajes.
- Grafana no necesita acceso a las bases operacionales.
- La instancia analítica puede tener un tamaño menor.
- Hay dos endpoints, dos backups y dos configuraciones que mantener.
- El coste aumenta; se mitiga con el *free tier*, instancias micro y destrucción tras
  la demo.
- El plan B está documentado y probado sobre el papel, de modo que el equipo no queda
  bloqueado si AWS no coopera.
- El informe debe explicar por qué se eligieron dos instancias y en qué condiciones se
  usaría una.

## Referencias

- [`docs/DATA-ARCHITECTURE.md`](../DATA-ARCHITECTURE.md), secciones 6 y 13.
- [`docs/PLAN.md`](../PLAN.md), secciones 6 y 23.
- [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md), sección 10.
