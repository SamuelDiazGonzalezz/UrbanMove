# 0008. MVP Vertical: "Ver y Reservar" en Java Hexagonal

- **Estado:** Aceptada / Implementada
- **Fecha:** 2026-11-16
- **Decisores:** Equipo UrbanMove

## Contexto

Para evitar desarrollar semanas a ciegas ("parálisis por análisis" y desconexión con la ejecución), se define un MVP vertical temprano enfocado en el objetivo mínimo del usuario: entrar a una web sin autenticación previa, ver vehículos disponibles sobre el mapa de la ciudad y realizar una reserva en tiempo real.

## Decisión

1. **Arquitectura Hexagonal (Puertos y Adaptadores):**
   - El paquete `domain` es Java puro (Java 21 `records`, enums e interfaces). No tiene dependencias de Spring, Jakarta, JPA ni AWS.
   - Puertos de entrada: `GetVehiclesUseCase`, `ReserveVehicleUseCase`.
   - Puertos de salida: `VehicleRepositoryPort`, `ReservationRepositoryPort`, `EventPublisherPort`.
   - Capa de aplicación: `FleetApplicationService` orquesta validaciones y transiciones de estado.
   - Adaptadores de infraestructura aislados:
     - Web: `FleetController` exponiendo REST en `/api/v1`.
     - Persistencia: `PostgresVehicleRepositoryAdapter` sobre Spring Data JPA y PostgreSQL 16 (`availability_db`).
     - Mensajería Cloud: `LocalstackSqsEventPublisherAdapter` publicando eventos `ReservationCreatedEvent` en SQS (`LocalStack`).
   - Interfaz de usuario (UI): Página web servida por Spring Boot en `/index.html` con mapa interactivo en Leaflet.js y Tailwind CSS.

2. **Alineación con la Arquitectura de Datos:**
   - La persistencia del estado en tiempo real (evitar doble reserva y ACID) reside en PostgreSQL.
   - S3 se mantiene como Data Lake inmutable para eventos históricos, no como almacén de transacciones concurrentes.
   - DynamoDB queda preservado como alternativa futura/opcional de telemetría masiva IoT, no para el núcleo relacional.

## Consecuencias y Estado

- **Hecho:**
  - Estructura Maven multimódulo con `pom.xml` padre.
  - Implementación completa del dominio, aplicación e infraestructura para vehículos y reservas.
  - Migración inicial Flyway con flota de prueba geolocalizada en Valencia.
  - UI interactiva funcional en `http://localhost:8080`.
  - Integración con PostgreSQL y LocalStack SQS.
- **Pendiente para siguientes fases:**
  - Autenticación JWT con Keycloak (Fase 3).
  - Lambda/Worker consumiendo de SQS para persistir eventos en S3 (Fase 2).
  - Inicio y fin de viaje (`rides`).
