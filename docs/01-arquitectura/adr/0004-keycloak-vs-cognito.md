# 0004. Keycloak en local y Cognito en AWS

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Decisores:** Equipo UrbanMove

## Contexto

El enunciado exige login y separación de roles entre usuarios finales, mantenimiento y
administración. El equipo no tiene experiencia implementando autenticación y el
repositorio es público, así que no puede contener credenciales.

Implementar un servidor de autenticación propio (hash de contraseñas, sesiones,
refresh tokens, recuperación, MFA) es un proyecto en sí mismo y una fuente habitual de
vulnerabilidades.

## Decisión

- **Local:** Keycloak en Docker, con un realm `urbanmove` versionado (sin secretos
  reales) y roles `USER`, `MAINTENANCE`, `ADMIN`.
- **AWS:** Cognito User Pool con grupos equivalentes, si hay cuenta.
- Flujo **OIDC Authorization Code + PKCE**.
- Los microservicios validan el JWT con Spring Security (`oauth2-resource-server`).
- El microservicio `identity` actúa como fachada de administración de usuarios y roles;
  no emite tokens ni gestiona contraseñas.
- Los secretos de clientes van en `.env` local o Secrets Manager/SSM en AWS.

## Alternativas consideradas

### A. Login propio en el servicio `identity`

- **Ventaja:** control total y menos dependencias.
- **Inconveniente:** hay que implementar hash, sesiones, tokens, refresh, bloqueo,
  recuperación y MFA; es fácil equivocarse y no aporta nota frente a un IdP estándar.
- **Descartada.**

### B. Spring Authorization Server

- **Ventaja:** todo en Java y dentro del ecosistema Spring.
- **Inconveniente:** más configuración y código propio; el equipo tendría que mantener
  el servidor OAuth.
- **Descartada para el mínimo;** podría ser una ampliación.

### C. Auth0 u otro SaaS

- **Ventaja:** rápido.
- **Inconveniente:** dependencia externa, límites del plan gratuito y configuración de
  callback; Keycloak local es más reproducible.
- **Descartada.**

## Consecuencias

- El login es estándar, seguro y demostrable con la pantalla de Keycloak.
- Los servicios solo tienen que validar JWT y mapear roles.
- El servicio `identity` sigue existiendo y cumple el requisito del enunciado como
  fachada de administración.
- Hay que documentar la diferencia entre "autenticación delegada" y "gestión de
  identidad".
- En AWS, si se usa Cognito, hay que adaptar el emisor y los grupos; el código de
  Spring Security es el mismo.

## Referencias

- Enunciado, secciones 3 y 4.1.
- [`docs/00-gestion/PLAN.md`](../../00-gestion/PLAN.md), sección 14.
- [`docs/01-arquitectura/ARCHITECTURE.md`](../../01-arquitectura/ARCHITECTURE.md), sección 11.
