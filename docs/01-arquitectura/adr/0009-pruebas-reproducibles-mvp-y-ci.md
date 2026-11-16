# 0009. Estrategia de Pruebas Reproducibles del MVP y CI

- **Estado:** Aceptada / Implementada
- **Fecha:** 2026-11-16
- **Decisores:** Equipo UrbanMove

## Contexto

El MVP vertical ("Ver y Reservar") requiere validación automática y reproducible. En lugar de depender de pruebas manuales por consola o llamadas directas ad-hoc, se implementa una suite de pruebas automatizadas según la pirámide definida en `docs/02-desarrollo/TESTING.md`.

Además, para evitar inconsistencias de versiones locales (e.g. diferencias entre Java 21 LTS y versiones superiores o falta de Maven en PATH del host), las pruebas deben poder ejecutarse de forma idéntica en local y en integración continua (GitHub Actions).

## Decisión

### 1. Desglose de Pruebas Implementadas

1. **Pruebas de Dominio Puro (`VehicleDomainTest`):**
   - **Tecnología:** JUnit 5 + AssertJ (sin contexto Spring, Java puro).
   - **Alcance:**
     - Validación de reserva en vehículos disponibles con batería suficiente.
     - Bloqueo y excepciones de negocio (`IllegalStateException`) si la batería es inferior al 15%.
     - Bloqueo de reserva si el vehículo está en `MAINTENANCE` o `RESERVED`.
   - **Rendimiento:** Ejecución en ~48 ms.

2. **Pruebas de Capa de Aplicación (`FleetApplicationServiceTest`):**
   - **Tecnología:** JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`).
   - **Alcance:**
     - Orquestación del caso de uso de reserva: persistencia de entidad actualizada y creación de reserva.
     - Verificación estricta de emisión del evento cloud `ReservationCreatedEvent` para LocalStack SQS mediante `ArgumentCaptor`.
     - Manejo de excepciones si el vehículo no existe (`IllegalArgumentException`) garantizando que no se emiten eventos espurios.

3. **Pruebas Web Slice (`FleetControllerTest`):**
   - **Tecnología:** `@WebMvcTest(FleetController.class)` con `MockMvc`.
   - **Alcance:**
     - `GET /api/v1/vehicles`: Serialización JSON correcta y código HTTP 200.
     - `POST /api/v1/reservations`: Deserialización de payload, delegación al caso de uso y código HTTP 201 Created.

### 2. Ejecución Aislada en Contenedor (Paridad con CI)

Se adopta la ejecución de pruebas dentro de un contenedor oficial efímero:
```bash
docker run --rm -v "${PWD}:/app" -w /app maven:3.9.9-eclipse-temurin-21 mvn -pl services/core-fleet test
```

**Motivos:**
- **Paridad de entorno 1:1:** Ejecuta exactamente en Linux con Java 21 LTS, eliminando el problema de "en mi máquina funciona".
- **Sin dependencias en el host:** No requiere instalar Maven ni configurar variables de entorno en Windows.
- **Garantía para GitHub Actions:** Como GitHub Actions ejecuta sobre `ubuntu-latest` con JDK 21, si la prueba pasa en este contenedor, el pipeline de CI pasa sin sorpresas.

### 3. Pipeline de CI Mínimo (`.github/workflows/ci.yml`)

Se configura el workflow básico de GitHub Actions que:
1. Hace checkout del código.
2. Configura JDK 21 Temurin con caché de Maven.
3. Ejecuta `mvn -B clean test`.

## Consecuencias y Evidencias

- 7 pruebas ejecutadas: 0 fallos, 0 errores.
- Tiempo de ejecución verificado: ~2.5 minutos (descarga y resolución inicial de plugins).
- Pipeline de CI listo para activarse en pull requests o pushes a `main` y `fase0-skeleton`.
