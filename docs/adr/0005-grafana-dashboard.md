# 0005. Grafana + Swagger/Postman como interfaces iniciales

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Decisores:** Equipo UrbanMove

## Contexto

El enunciado exige tres interfaces diferenciadas: app de usuario, app de
mantenimiento/operaciones y dashboard operativo. También permite explícitamente que
sean "apps web sencillas, APIs documentadas, colecciones de peticiones o clientes CLI".

El equipo no tiene la interfaz como prioridad y el tiempo es limitado. Construir tres
frontends propios (React, autenticación, estado, despliegue) consumiría semanas que son
más valiosas para los microservicios, los eventos, el Data Lake y los marts.

## Decisión

- **App de usuario:** Swagger UI + colección Postman/Newman.
- **App de mantenimiento:** Swagger UI + colección Postman/Newman.
- **Dashboard operativo:** Grafana con dos datasources:
  - PostgreSQL (data marts de negocio).
  - Prometheus (salud y métricas técnicas).
- El login se demuestra con la pantalla de Keycloak/Cognito y el JWT en Postman.
- Una web propia queda como ampliación opcional, no como camino crítico.

## Alternativas consideradas

### A. Tres aplicaciones React

- **Ventaja:** experiencia de usuario y presentación.
- **Inconveniente:** mucho tiempo de frontend, autenticación, build, despliegue y
  pruebas; no es donde está el peso de la rúbrica.
- **Aplazada como ampliación.**

### B. Un BFF Spring Boot con Thymeleaf/HTMX

- **Ventaja:** todo en Java, sin Node.
- **Inconveniente:** sigue siendo una interfaz que mantener y no aporta al mínimo.
- **Aplazada.**

### C. Metabase o Superset

- **Ventaja:** dashboards potentes.
- **Inconveniente:** otra herramienta que aprender y desplegar; Grafana ya cubre
  negocio y métricas técnicas.
- **Descartada.**

## Consecuencias

- El equipo dedica el tiempo a lo que más pondera: arquitectura, implementación,
  eventos, analítica, fallos y DevOps.
- Grafana permite enseñar indicadores reales antes/después de una simulación.
- Swagger/Postman son defendibles porque el enunciado los acepta.
- Si sobra tiempo, se añade una web mínima sin rehacer el backend.

## Referencias

- Enunciado, sección 3.
- [`docs/PLAN.md`](../PLAN.md), sección 24.
- [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md), sección 9.
