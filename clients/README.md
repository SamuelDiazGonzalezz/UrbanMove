# Interfaces de cliente

El enunciado exige tres interfaces diferenciadas. La prioridad del proyecto es el
backend, así que se empieza con herramientas que ya proporcionan interfaz y se
deja una web propia como ampliación.

| Interfaz | Implementación prevista | Estado |
|---|---|---|
| App de usuarios | Swagger UI + colección Postman/Newman | Pendiente |
| App de mantenimiento | Swagger UI + colección Postman/Newman | Pendiente |
| Dashboard operativo | Grafana (PostgreSQL + Prometheus) | Pendiente |

## Ampliación opcional

Una aplicación web propia (React/Vite o Spring Boot + Thymeleaf) con tres áreas:
usuario, operaciones y dashboard. No es camino crítico y no debe retrasar los
cinco flujos obligatorios de la defensa.

## Login

Keycloak (local) o Cognito (AWS) proporcionan la pantalla de login. Las herramientas
cliente obtienen un JWT y lo envían como `Authorization: Bearer <token>`.
