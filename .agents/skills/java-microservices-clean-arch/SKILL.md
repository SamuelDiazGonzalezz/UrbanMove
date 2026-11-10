---
name: java-microservices-clean-arch
description: Directrices, arquitectura hexagonal/DDD, resiliencia y diseño de APIs REST modernas para microservicios Java (Spring Boot 3+ / Quarkus).
---

# Java Microservices Clean Architecture & Resilience

Esta skill proporciona las mejores prácticas y patrones de diseño para microservicios en Java modernos (Java 17/21+, Spring Boot 3.x o Quarkus).

## 1. Arquitectura Hexagonal (Ports & Adapters) y DDD

Estructura de paquetes recomendada por microservicio o bounded context:

```text
com.example.service/
├── domain/                    # Núcleo de negocio puro (SIN dependencias de frameworks)
│   ├── model/                 # Entidades y Value Objects (inmutables, Records)
│   ├── exception/             # Excepciones de dominio
│   └── repository/            # Interfaces de salida (Output Ports)
├── application/               # Casos de uso y orquestación
│   ├── port/
│   │   ├── in/                # Input Ports (interfaces para casos de uso)
│   │   └── out/               # Output Ports (interfaces para mensajería, clientes ext.)
│   └── service/               # Implementación de los casos de uso
└── infrastructure/            # Adaptadores externos y frameworks
    ├── adapter/
    │   ├── in/
    │   │   └── rest/          # Controladores REST, DTOs de entrada/salida, Mappers
    │   └── out/
    │       ├── persistence/   # Entidades JPA, repositorios Spring Data, adaptadores DB
    │       └── messaging/     # Productores/consumidores Kafka/RabbitMQ
    └── config/                # Beans de configuración de Spring/Quarkus
```

### Reglas Clave:
1. **Independencia del Dominio**: El paquete `domain` NUNCA debe importar anotaciones de Spring, JPA/Hibernate (`@Entity`, `@Table`) ni Jackson. El dominio se modela con POJOs/Records puros.
2. **Puertos e Interfaces**: Las dependencias siempre apuntan hacia adentro (Infrastructure -> Application -> Domain). La infraestructura implementa los puertos definidos en el dominio o aplicación.

---

## 2. Diseño de APIs REST y Manejo de Errores (RFC 7807)

### Estándar RFC 7807 (`ProblemDetail` en Spring Boot 3+)
Utiliza el estándar RFC 7807 para todas las respuestas de error en lugar de estructuras arbitrarias:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Error de validación de campos");
        problem.setTitle("Validación fallida");
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        problem.setProperty("invalidParams", errors);
        return problem;
    }
}
```

### Documentación con OpenAPI (SpringDoc):
- Anotar DTOs con `@Schema(description = "...", example = "...")`.
- Definir respuestas esperadas en controladores con `@ApiResponse`.

---

## 3. Resiliencia y Tolerancia a Fallos (Resilience4j)

En entornos distribuidos de microservicios, cualquier llamada a otro servicio o base de datos debe estar protegida frente a fallos en cascada.

### Patrón Circuit Breaker + Retry:

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      externalService:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
  retry:
    instances:
      externalService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff:
          multiplier: 2
```

```java
@Service
public class ExternalApiClient {

    @CircuitBreaker(name = "externalService", fallbackMethod = "fallbackGetProfile")
    @Retry(name = "externalService")
    public UserProfile fetchUserProfile(String userId) {
        return restClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .body(UserProfile.class);
    }

    public UserProfile fallbackGetProfile(String userId, Throwable t) {
        log.warn("Fallback invocado para userId: {} debido a: {}", userId, t.getMessage());
        return UserProfile.cachedOrEmpty(userId);
    }
}
```
