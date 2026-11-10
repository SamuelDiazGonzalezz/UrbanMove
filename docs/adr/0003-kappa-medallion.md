# 0003. Kappa + Medallón para la capa analítica

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Decisores:** Equipo UrbanMove

## Contexto

El enunciado exige un Data Lake con capas raw, trusted y curated, al menos cuatro data
marts y una analítica que no bloquee las operaciones. También exige que los eventos
alimenten el lago y que se pueda reprocesar.

El equipo ha estudiado tres arquitecturas de datos: Medallón, Lambda y Kappa. Kappa es
la que mejor encaja con un sistema event-driven y con el requisito de que el flujo
principal no dependa de la analítica.

## Decisión

Se combinan dos ideas:

1. **Kappa** como estilo de procesamiento: todos los eventos entran por un único
   pipeline de streaming; el log de eventos es la fuente de verdad; para reprocesar se
   reproduce el log.
2. **Medallón** como organización física del lago:
   - `raw`: evento tal cual llega.
   - `trusted`: validado, deduplicado y normalizado.
   - `curated`: agregados y data marts.

No se implementa una capa batch separada. El batch se sustituye por reproducción del
log cuando hace falta reconstruir un mart.

## Alternativas consideradas

### A. Lambda architecture

- **Ventaja:** separa batch histórico y speed layer; es un patrón clásico.
- **Inconveniente:** duplica la lógica (batch y streaming), añade complejidad
  operativa y no aporta ventajas claras para el volumen del proyecto.
- **Descartada:** el equipo no necesita dos caminos de procesamiento.

### B. Solo batch

- **Ventaja:** sencillo de implementar.
- **Inconveniente:** no cumple el requisito de eventos en tiempo real ni el de
  tolerancia a fallos del flujo operacional.
- **Descartada.**

### C. Data Mesh

- **Ventaja:** descentralización por dominios.
- **Inconveniente:** es un modelo organizativo, no una arquitectura técnica; exige
  equipos y gobierno de datos que no existen en un proyecto académico.
- **Descartada.**

## Consecuencias

- El pipeline es más simple: un solo camino de ingesta.
- La reconstrucción de marts se hace releyendo raw, lo que es reproducible.
- El rendimiento del reprocesamiento depende del volumen; para el proyecto es
  aceptable.
- Hay que cuidar la deduplicación por `eventId` y el particionado.
- El informe debe explicar por qué no se usa Lambda architecture y cómo se cubre la
  reprocesabilidad con Kappa.

## Referencias

- Enunciado, sección 8.
- [`docs/PLAN.md`](../PLAN.md), sección 12.
- [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md), sección 8.
