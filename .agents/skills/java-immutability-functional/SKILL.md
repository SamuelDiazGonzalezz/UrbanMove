---
name: java-immutability-functional
description: Patrones de inmutabilidad en Java moderno (Java 17/21+), Records, tipos de datos algebraicos (sealed interfaces), colecciones inmodificables, thread-safety y verificación con ArchUnit.
---

# Inmutabilidad y Programación Funcional en Java Moderno

Esta skill define los principios incondicionales para garantizar inmutabilidad, thread-safety y ausencia de efectos colaterales en aplicaciones Java.

## 1. Principio Fundamental: Inmutable por Defecto

1. **DTOs, Mensajes de Eventos y Value Objects**: Siempre deben ser `record` de Java.
2. **Entidades de Dominio**: Si no se requiere identidad mutable (como en JPA), modelar con `record`. Si se usa persistencia ORM, encapsular mutaciones en métodos de negocio específicos y NUNCA usar setters genéricos (`setFoo(...)`).
3. **Campos `final` y Beans Stateless**: Todos los servicios `@Service`, `@Component` y controladores deben ser completamente sin estado (stateless) y tener dependencias inyectadas por constructor en campos `private final`.

---

## 2. Records con Validación Compacta (Compact Constructors)

Los `record` garantizan que todos sus atributos son `private final`, tienen accesores generados automáticamente, y ofrecen `equals`/`hashCode`/`toString` consistentes.

```java
public record Coordinate(double latitude, double longitude) {
    // Compact constructor: validación invariante al instanciar
    public Coordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitud inválida: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitud inválida: " + longitude);
        }
    }
}
```

---

## 3. Colecciones Inmodificables y Copias Defensivas

**Nunca** almacenar ni retornar referencias directas a colecciones mutables (`ArrayList`, `HashSet`). 

```java
public record FleetGroup(String groupId, List<Vehicle> vehicles) {
    // Copia defensiva en el constructor para evitar mutaciones externas:
    public FleetGroup {
        vehicles = List.copyOf(vehicles); // List.copyOf produce una lista 100% inmutable
    }
}
```

- Para inicializar colecciones fijas: `List.of(...)`, `Set.of(...)`, `Map.of(...)`.
- En streams: usar `.toList()` en lugar de `.collect(Collectors.toList())` (el primero devuelve una lista inmutable en Java 16+).

---

## 4. Tipos de Datos Algebraicos con `sealed interface` y Pattern Matching

Para representar estados de negocio excluyentes, utilizar interfaces selladas (`sealed`) con `records`:

```java
public sealed interface VehicleStatus permits VehicleStatus.Parked, VehicleStatus.InTransit, VehicleStatus.Maintenance {
    
    record Parked(Coordinate location, Instant since) implements VehicleStatus {}
    record InTransit(Coordinate current, double speedKmH, String routeId) implements VehicleStatus {}
    record Maintenance(String reason, Instant estimatedReturn) implements VehicleStatus {}
}
```

Uso exhaustivo con Pattern Matching (`switch` expression de Java 21):

```java
public String describe(VehicleStatus status) {
    return switch (status) {
        case VehicleStatus.Parked p -> "Aparcado en " + p.location();
        case VehicleStatus.InTransit t -> "En tránsito a " + t.speedKmH() + " km/h";
        case VehicleStatus.Maintenance m -> "En mantenimiento: " + m.reason();
    }; // El compilador verifica que todos los casos están cubiertos sin necesidad de 'default'
}
```

---

## 5. Verificación Automática de Inmutabilidad con ArchUnit

Añadir una prueba con ArchUnit para garantizar que nadie introduzca setters o clases mutables por error:

```java
@AnalyzeClasses(packages = "com.example.service.domain")
class ArchitectureTests {

    @ArchTest
    static final ArchRule no_setters_in_domain = 
        noMethods().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..")
        .should().haveNameStartingWith("set")
        .because("El modelo de dominio debe ser inmutable y no admitir setters");
}
```
