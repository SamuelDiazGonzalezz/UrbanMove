# Reglas de Desarrollo: Java Microservices, DevOps e Inmutabilidad

Para este proyecto y el desarrollo de microservicios en Java, se deben seguir de forma estricta las siguientes directrices:

## 1. Versión y Sintaxis del Lenguaje
- **Java 21+** como versión objetivo estándar.
- Emplear **Java Records** para todos los DTOs, objetos de valor (Value Objects), mensajes de eventos y respuestas REST.
- Emplear **Sealed Interfaces** y `record` para modelar estados del dominio con tipos de datos algebraicos.
- Aprovechar las expresiones `switch` exhaustivas y el pattern matching.

## 2. Inmutabilidad y Estado
- **Inmutabilidad por defecto**: Todo campo debe ser `final` salvo imposibilidad técnica explícita.
- **Colecciones inmodificables**: Usar `List.of()`, `Set.of()`, `Map.of()` y copias defensivas (`List.copyOf()`). En Streams usar `.toList()`.
- **Servicios Stateless**: Todas las clases `@Service`, `@Component` y `@RestController` no deben tener estado mutable interno. Las dependencias se inyectan en campos `private final` mediante constructor (o `@RequiredArgsConstructor`).
- **Prohibidos los Setters indiscriminados**: El dominio nunca debe exponer setters estilo JavaBean salvo entidades JPA donde sea estrictamente necesario y justificado.

## 3. SOLID, Ley de Deméter e Interfaces sobre Herencia
- **Single Responsibility (SRP) e Interface Segregation (ISP)**: Casos de uso atómicos (`RegisterVehicleAssetUseCase`, etc.) e interfaces de puertos especializadas (`SaveAssetPort`, etc.).
- **Ley de Deméter (Tell, Don't Ask)**: Prohibido encadenar accesores (`a.getB().getC()`). Delegar las operaciones directamente al objeto que posee los datos.
- **Interfaces y Composición sobre Herencia**: NUNCA crear jerarquías de clases abstractas base (`BaseEntity`, `BaseService`, `AbstractCrudController`). Compartir código mediante composición e inyección de dependencias.
- **Nomenclatura Unívoca e Identificable**: En un monorepo multimicroservicio, no usar nombres genéricos (`Service`, `Vehicle`, `Entity`). Emplear siempre el contexto del bounded context (ej. `VehicleAsset`, `VehicleAssetRepositoryPort`, `ResourceInventoryController`).

## 4. Arquitectura y APIs REST
- Aplicar principios de **Arquitectura Hexagonal (Ports & Adapters)**: separar estrictamente el dominio de la infraestructura (frameworks, bases de datos, clientes HTTP).
- Las respuestas de error REST deben seguir el estándar **RFC 7807 (`ProblemDetail`)**.
- Documentación OpenAPI 3.0 mediante anotaciones SpringDoc en endpoints y modelos.
- Tolerancia a fallos mediante **Resilience4j** (Circuit Breakers y Retries) en todas las comunicaciones I/O con servicios externos.

## 5. Pruebas y Calidad
- Pruebas unitarias con **JUnit 5** y **AssertJ** (`assertThat(...)`).
- Pruebas de integración con **Testcontainers** (PostgreSQL, Kafka, etc.) en lugar de bases de datos H2 en memoria.
- Asincronía testeada con **Awaitility**, nunca con `Thread.sleep()`.

## 6. DevOps y Contenedores
- Empaquetado en Dockerfiles **multi-stage** optimizados con capas Spring Boot y base **Eclipse Temurin JRE (Alpine)**.
- Ejecutar contenedores con usuario sin privilegios (`appuser`).
- Ajustar flags de la JVM para contenedores: `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`.
- Habilitar probes de Kubernetes (`/actuator/health/liveness`, `/actuator/health/readiness`) y parada limpia (`server.shutdown=graceful`).
