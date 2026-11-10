# Microservicios

Cada carpeta de este directorio será un **microservicio atómico e independiente**:
su propio `pom.xml`, su propio artefacto desplegable, su propia base de datos y sus
propios contratos.

## Catálogo previsto

| # | Servicio | Responsabilidad | Estado |
|---|---|---|---|
| 1 | `identity` | Autenticación delegada, usuarios, roles y sesión | Pendiente |
| 2 | `user-profiles` | Perfil, preferencias y baja lógica | Pendiente |
| 3 | `zones` | Catálogo de zonas y restricciones | Pendiente |
| 4 | `inventory` | Activos físicos y adquisiciones | Pendiente |
| 5 | `availability` | Estado operativo y zona actual del recurso | Pendiente |
| 6 | `telemetry` | Batería, ubicación, kilómetros y usos | Pendiente |
| 7 | `reservations` | Reservas, cancelación y expiración | Pendiente |
| 8 | `rides` | Inicio, fin, duración y coste preliminar | Pendiente |
| 9 | `incidents` | Averías, vandalismo y bloqueos | Pendiente |
| 10 | `maintenance` | Órdenes de retirada, recarga y reparación | Pendiente |
| 11 | `relocation` | Órdenes de recolocación | Pendiente |
| 12 | `history` | Historial independiente por eventos | Pendiente |
| 13 | `analytics` | Consultas agregadas sobre data marts | Pendiente |

> Los 10 primeros de la lista de obligatorios del enunciado son: identidad, perfiles,
> inventario, disponibilidad, reservas, viajes, historial, mantenimiento, recolocación
> y analítica operacional. `zones`, `telemetry` e `incidents` son servicios finos
> adicionales para cubrir los flujos funcionales.

## Reglas

- Sin clases base abstractas compartidas.
- Sin acceso directo a la base de datos de otro servicio.
- Contratos explícitos: OpenAPI para REST y JSON Schema/AsyncAPI para eventos.
- Comunicación síncrona solo cuando sea imprescindible; el resto, eventos.
- Cada servicio debe arrancar solo con su configuración y su base de datos.
