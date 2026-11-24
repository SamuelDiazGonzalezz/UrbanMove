# ADR 0013 — El Prototipo 1 sustituye a `core-fleet` como arquitectura vigente

| Campo       | Valor                          |
|-------------|--------------------------------|
| **Estado**  | Aceptado                       |
| **Fecha**   | 2026-11-24                     |
| **Autores** | Equipo UrbanMove               |
| **Sustituye** | [ADR 0012](0012-core-fleet-monolito-modular-hasta-split.md) |

---

## Contexto

El [ADR 0012](0012-core-fleet-monolito-modular-hasta-split.md) decidió mantener tres dominios
(`availability`, `reservations`, `rides`) dentro de un monolito modular llamado `core-fleet`,
con un *split* a microservicios planificado para más adelante.

Ese *split* ya se ha ejecutado en la rama `prototipo1`:

- 13 microservicios independientes en `services/` con base de datos propia.
- Comunicación por eventos (EventBridge + SQS + DLQ) y por HTTP interno.
- 5 Lambdas que consumen eventos y alimentan el Data Lake y los data marts.
- Terraform para la mensajería, las Lambdas y S3, y Docker Compose para la plataforma local.
- Tres interfaces: usuario, operaciones y dashboard analítico.

Mientras tanto, `services/core-fleet` sigue en el reactor Maven y en el CI, y varios documentos
(`ESTADO-Y-PENDIENTES-2026-11-21*.md`, `p0-remediation-design.md`, `MANUAL-PRUEBAS-MVP.md`,
`PLAN.md`, `ACTION-PLAN.md`) describen ese monolito como si fuera el sistema actual. Además,
`core-fleet` contiene la única implementación robusta de outbox con reintentos y *dead-letter*,
lo que hace creer que esa deuda está cerrada para los 13 servicios cuando no lo está.

## Decisión

1. **La arquitectura vigente es el Prototipo 1**, descrito en
   [`docs/02-desarrollo/PROTOTIPO-1.md`](../../02-desarrollo/PROTOTIPO-1.md) y en el código de la
   rama `prototipo1`.
2. **`core-fleet` pasa a legado explícito.** Se conserva el código y sus tests como evidencia
   histórica del MVP de Fase 0, pero:
   - queda fuera del reactor Maven por defecto (perfil `legacy`);
   - no forma parte de la arquitectura desplegada ni de la documentación vigente;
   - no se refactoriza ni se amplía.
3. **Los documentos de Fase 0 se marcan como históricos** y no se usan para implementar.
4. **La hoja de ruta operativa** pasa a ser
   [`docs/00-gestion/DEUDA-Y-ROADMAP-2026-11-24.md`](../../00-gestion/DEUDA-Y-ROADMAP-2026-11-24.md),
   con el plan de Terraform completo en
   [`docs/03-operacion/TERRAFORM-FULL.md`](../../03-operacion/TERRAFORM-FULL.md).

## Consecuencias

**Positivas**

- Una sola arquitectura de referencia para el equipo y para cualquier agente.
- El CI deja de gastar tiempo y señal de calidad en un módulo que no se despliega.
- La deuda de outbox de `platform-common` deja de estar oculta por la implementación del legado.

**Negativas**

- Se pierde la red de seguridad que aportaban los 13 tests de `core-fleet`; hay que compensarla
  con tests reales en los 13 servicios (PR-3 de la hoja de ruta).
- Hay que corregir enlaces, banners e índices en varios documentos.

## Alternativas descartadas

| Alternativa | Motivo del descarte |
|---|---|
| Borrar `core-fleet` | Se pierde la evidencia del MVP de Fase 0 y su historial de Git no es suficiente para la memoria del proyecto |
| Mantener `core-fleet` como módulo activo | Duplica arquitecturas, contamina la cobertura y confunde la defensa |
| Seguir ampliando `core-fleet` | Contradice el objetivo de 10+ servicios independientes de la rúbrica |

## Referencias

- [ADR 0012 — `core-fleet` como monolito modular](0012-core-fleet-monolito-modular-hasta-split.md)
- [Prototipo 1](../../02-desarrollo/PROTOTIPO-1.md)
- [Deuda y hoja de ruta](../../00-gestion/DEUDA-Y-ROADMAP-2026-11-24.md)
- [Terraform full](../../03-operacion/TERRAFORM-FULL.md)
