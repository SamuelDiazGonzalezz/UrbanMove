---
name: java-clean-code-solid-demeter
description: Principios de diseño de software limpio para Java y microservicios: Ley de Deméter, SOLID, Interfaces sobre Herencia (Composición), Tell-Don't-Ask y Nomenclatura Unívoca en arquitecturas monorepo/distribuidas.
---

# Clean Code, SOLID y Ley de Deméter en Java Moderno

Esta skill define las reglas obligatorias de diseño orientado a objetos y arquitectura limpia para el desarrollo de microservicios Java desacoplados, robustos y mantenibles.

---

## 1. Principios SOLID Aplicados

### S - Responsabilidad Única (Single Responsibility Principle - SRP)
- Cada clase o `record` debe tener una única responsabilidad bien acotada y una única razón para cambiar.
- Separar estrictamente casos de uso: en vez de una interfaz monolítica `VehicleService` con 20 métodos, usar casos de uso específicos: `RegisterVehicleAssetUseCase`, `RetireVehicleAssetUseCase`, `GetVehicleAssetUseCase`.
- Separar DTOs de entrada, DTOs de salida, modelos de dominio y entidades de persistencia.

### O - Abierto/Cerrado (Open/Closed Principle - OCP)
- Las clases deben estar abiertas a la extensión pero cerradas a la modificación.
- Usar polimorfismo mediante interfaces, Records o `sealed interfaces` para nuevos comportamientos sin modificar código existente.

### L - Sustitución de Liskov (Liskov Substitution Principle - LSP)
- Las implementaciones de una interfaz deben satisfacer todos los contratos pre y postcondición de la misma.
- Prohibido lanzar `UnsupportedOperationException` en métodos de una interfaz que no aplican a una subclase (violación directa de LSP e ISP).

### I - Segregación de Interfaces (Interface Segregation Principle - ISP)
- Los clientes no deben verse obligados a depender de métodos que no utilizan.
- Crear interfaces pequeñas, cohesivas y especializadas (ej. `FindAssetPort`, `SaveAssetPort`, `PublishAssetEventPort`) o puertos de entrada granulares por caso de uso.

### D - Inversión de Dependencias (Dependency Inversion Principle - DIP)
- Los módulos de alto nivel (dominio y casos de uso) no deben depender de módulos de bajo nivel (JPA, MongoDB, Kafka, REST).
- Ambos dependen de abstracciones (puertos/interfaces). La infraestructura depende del dominio, nunca al revés.

---

## 2. Ley de Deméter (Principle of Least Knowledge) & "Tell, Don't Ask"

### La Regla
Un método `m` de una clase `C` solo debe invocar métodos de:
1. La propia clase `C`.
2. Objetos pasados como parámetros a `m`.
3. Objetos creados o instanciados directamente dentro de `m`.
4. Dependencias directas inyectadas en `C` (campos `private final`).

### Anti-patrón: Tren de Invocaciones (Train Wrecks)
```java
// ❌ VIOLACIÓN DE DEMÉTER: Acoplamiento a la estructura interna de múltiples objetos
String city = vehicle.getLocation().getCoordinates().getCity().getName();
```

### Solución: "Tell, Don't Ask" y Delegación
```java
// ✅ CORRECTO: Pedir al objeto raíz que realice la acción o responda directamente
boolean inZone = vehicle.isLocatedInZone(zoneId);
```
No inspecciones el estado interno de un objeto para tomar decisiones por él; dile al objeto que ejecute la lógica de negocio.

---

## 3. Interfaces sobre Herencia (Composition Over Inheritance)

- **Prohibido el uso de jerarquías profundas de herencia**: Nunca crear `BaseEntity`, `BaseService`, `AbstractCrudController` o `GenericManager`.
- La herencia introduce acoplamiento estático severo y fragilidad de clases base (*Fragile Base Class problem*).
- **Regla**:
  - Modelar contratos mediante **interfaces**.
  - Compartir comportamiento mediante **composición** (delegación de dependencias inyectadas).
  - Modelar variantes cerradas de datos mediante **`sealed interfaces`** con `record`.

---

## 4. Nomenclatura Unívoca e Identificable en Entornos Multimicroservicio

En un monorepo o ecosistema con múltiples microservicios (inventario, reservas, viajes, telemetría, etc.), los nombres genéricos causan ambigüedad y colisiones.

### Reglas de Nombres:
1. **Contexto Explícito**: Incluir el prefijo o contexto del microservicio en clases clave:
   - ❌ Evitar: `Vehicle`, `Entity`, `Service`, `Repository`, `Controller`.
   - ✅ Usar: `VehicleAsset` (en Inventario), `AvailableResource` (en Disponibilidad), `VehicleTelemetry` (en Telemetría).
2. **Puertos y Adaptadores Claros**:
   - Casos de uso: `RegisterVehicleAssetUseCase`, `FindVehicleAssetByIdUseCase`.
   - Puertos de salida: `VehicleAssetRepositoryPort`, `VehicleAssetEventPublisherPort`.
   - Adaptadores: `VehicleAssetRestAdapter` (o `VehicleAssetController`), `VehicleAssetJpaRepositoryAdapter`.
3. **Comandos y Eventos**:
   - Comandos: `RegisterVehicleAssetCommand`, `DecommissionAssetCommand`.
   - Eventos de dominio: `VehiclePurchasedEvent`, `VehicleRetiredEvent`.
