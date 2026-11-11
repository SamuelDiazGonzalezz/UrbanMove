# Uso de GitHub: lo mínimo que nos sirve

> **Estado:** propuesta para revisión del equipo.
> **Última actualización:** 2026-11-11.
> **Ámbito:** cómo se traza el trabajo con issues y etiquetas, y por qué **no** se usan
> milestones ni Projects.
> **Documentos relacionados:** [KICKOFF.md](KICKOFF.md) (reparto y primeras acciones),
> [ACTION-PLAN.md](ACTION-PLAN.md) (calendario semanal), [PLAN.md](PLAN.md) (fases) y
> [SERVICE-ROADMAP.md](../01-arquitectura/SERVICE-ROADMAP.md) (orden de servicios).

---

## 0. La decisión, en una tabla

| Herramienta | ¿La usamos? | Coste | Motivo |
|---|---|---|---|
| **Issues** | Sí, pero pocas | ~1 minuto por issue | Es la única forma de saber quién hace qué |
| **Etiquetas** | Sí, 5 como máximo | 10 minutos una vez | Filtrar por fase y por bloqueo |
| **Milestones** | **No** | Mantener dos calendarios | Las fases ya están en `PLAN.md` |
| **Projects (Kanban)** | **No** | Actualizar un tablero más | Con 3 personas sobra con la lista de issues |

**En una frase:** el plan vive en Markdown; las issues solo responden "¿quién hace qué
esta semana?". Todo lo que duplique el plan es trabajo extra que no se va a mantener.

Si el equipo decide no usar issues, el proyecto sigue funcionando: se pierde
trazabilidad para el informe, no capacidad de construir. La sección 10 explica el modo
sin issues.

---

## 1. Qué es una issue

Una **issue** es una tarea con número (`#12`). Tiene título, descripción, un
responsable y un estado abierto/cerrado.

| Campo | Para qué |
|---|---|
| Título | Qué hay que hacer, en una frase |
| Descripción | Criterios de aceptación (2-3 líneas basta) |
| Assignee | Quién lo hace |
| Etiqueta | `fase-N` y, si toca, `bloqueante` |
| Estado | Abierta o cerrada |

Ejemplo:

```text
#12 [Fase 0] docker-compose de infraestructura

- postgres:16 con las 14 bases
- keycloak:26.8.2 y localstack:4.14.0
- healthchecks y volúmenes
- docker compose --profile infra up funciona

Asignado: C
Etiqueta: fase-0
```

No es un commit ni una rama: es el trabajo planificado. El commit es lo que lo resuelve.

---

## 2. Qué es un milestone (y por qué no lo usamos)

Un **milestone** agrupa issues con una fecha límite y muestra el progreso:
`7 of 12 issues closed · due Nov 17`.

Es útil cuando hay muchas personas y varios equipos. Aquí no:

- Las fases y sus fechas ya están en [PLAN.md](PLAN.md) y en el calendario semanal de
  [ACTION-PLAN.md](ACTION-PLAN.md).
- Con 3 personas, el progreso se ve mirando la lista de issues abiertas.
- Un milestone desactualizado es peor que no tenerlo, y mantenerlo cuesta tiempo cada
  semana.

**Excepción:** si la asignatura pide explícitamente evidencia de gestión de proyectos,
se crea **uno solo**, `Entrega final`, con la fecha de entrega. No se crean seis.

---

## 3. Qué es GitHub Projects (y por qué no lo usamos)

**Projects** es un tablero Kanban: columnas *Pendiente / En curso / Hecho* y una
tarjeta por issue. No aporta información nueva, solo reorganiza la que ya hay.

Con tres personas y una reunión semanal, el tablero se queda viejo en dos días. Si
algún día hace falta, se activa en 5 minutos; no hay que decidirlo ahora.

---

## 4. Cuándo abrir una issue y cuándo no

| Situación | ¿Issue? | Qué hacer |
|---|---|---|
| Cambio de una línea o una errata | No | Commit directo |
| Tarea de menos de medio día, sin dependencias | No hace falta | Rama y PR, con título claro |
| Tarea de más de medio día | Sí | Issue con criterios de aceptación |
| Algo que bloquea a otra persona | Sí, con `bloqueante` | Avisar en el grupo el mismo día |
| Decisión que cambia un contrato | Sí | Issue + actualizar el `.md` correspondiente |
| Bug que aparece en la demo | Sí | Issue con pasos para reproducir |

Regla práctica: **si alguien va a preguntar "¿en qué estás?", debe haber una issue.**
Si nadie lo va a preguntar, no hace falta burocracia.

---

## 5. Etiquetas: cinco y ya

No hace falta una etiqueta por servicio. Con estas cinco se filtra todo lo necesario:

| Etiqueta | Uso |
|---|---|
| `fase-0` … `fase-5` | Una por issue, según el roadmap |
| `bloqueante` | Impide avanzar a otra persona |
| `bug` | Defecto |
| `docs` | Documentación |
| `infra` | Compose, CI, AWS |

El nombre del servicio va en el **título**, no en una etiqueta:

```text
[Fase 1] Implementar availability de punta a punta
```

Crear las etiquetas una vez:

```bash
gh label create "fase-0" --color "0E8A16"
gh label create "fase-1" --color "0E8A16"
gh label create "fase-2" --color "0E8A16"
gh label create "fase-3" --color "0E8A16"
gh label create "fase-4" --color "0E8A16"
gh label create "fase-5" --color "0E8A16"
gh label create "bloqueante" --color "D93F0B"
gh label create "bug" --color "B60205"
gh label create "docs" --color "0075CA"
gh label create "infra" --color "5319E7"
```

---

## 6. Ciclo de una tarea

```text
Issue (si aplica) → rama → PR con "Closes #12" → revisión de otro → merge → cerrada
```

- Ramas: `feat/availability-double-booking`, `fix/reservation-expiry`,
  `docs/github-workflow`.
- **Nadie mergea su propio PR** sin revisión de otro.
- **Nadie trabaja más de tres días en una rama.** Si crece, se parte.
- El PR enlaza la issue para que el cierre sea automático.

---

## 7. Plantilla corta de issue

No hace falta una plantilla compleja. Con esto basta:

```markdown
## Objetivo
Qué tiene que existir al terminar.

## Criterios de aceptación
- [ ] ...
- [ ] ...

## Fuera de alcance
Lo que esta issue NO incluye.
```

Si se quiere automatizar, se guarda como `.github/ISSUE_TEMPLATE/tarea.md` en la Fase 0.

---

## 8. Las cinco issues de Fase 0

| # | Título | Dueño | Etiqueta |
|---|---|---|---|
| 1 | `[Fase 0] Estructura del repositorio y .env.example` | C | `fase-0` |
| 2 | `[Fase 0] docker-compose de infraestructura` | C | `fase-0` |
| 3 | `[Fase 0] POM padre y esqueleto de zones` | A | `fase-0` |
| 4 | `[Fase 0] Realm de Keycloak con roles` | B | `fase-0` |
| 5 | `[Fase 0] zones de punta a punta + CI verde` | A | `fase-0` |

Salen de las primeras acciones de [KICKOFF.md](KICKOFF.md), sección 12.

```bash
gh issue create \
  --title "[Fase 0] docker-compose de infraestructura" \
  --body "Criterios: postgres:16, keycloak:26.8.2, localstack:4.14.0, healthchecks." \
  --label "fase-0" \
  --assignee @me
```

Consultar:

```bash
gh issue list
gh issue list --label "bloqueante"
gh issue list --assignee @me
gh issue close 12
```

---

## 9. Cuánto cuesta de verdad

| Momento | Tiempo |
|---|---|
| Crear la issue | 1 minuto |
| Crear las 5 de Fase 0 | 10 minutos una vez |
| Revisar issues en la reunión semanal | 5 minutos |
| Actualizar una issue | 1 minuto |

Total: menos de **15 minutos por semana**. Si en algún momento cuesta más, se está
usando mal: se recorta, no se abandona.

---

## 10. Si decidimos no usar issues

Es una opción válida. En ese caso:

1. El **único** tablero es [ACTION-PLAN.md](ACTION-PLAN.md), sección 2 (calendario
   semanal), y [SERVICE-ROADMAP.md](../01-arquitectura/SERVICE-ROADMAP.md) (orden de
   dependencias).
2. Cada lunes se revisa en voz alta: qué se cerró, qué se hace esta semana, qué bloquea.
3. El acta de la reunión recoge los acuerdos en [KICKOFF.md](KICKOFF.md).
4. Para el informe se usa el histórico de commits y PR, que ya existe.

Lo que se pierde: una lista pública de "quién hace qué". Con tres personas se compensa
hablando, pero queda peor en la documentación de gestión.

**Recomendación:** usar issues solo para lo que dura más de medio día y para lo que
bloquea. No llenar el repositorio de tareas de una línea.

---

## 11. Checklist

- [ ] El equipo ha decidido si usa issues o solo el plan en Markdown.
- [ ] Si se usan, están creadas las etiquetas `fase-0`…`fase-5`, `bloqueante`, `bug`,
      `docs` e `infra`.
- [ ] Las 5 issues de Fase 0 existen, o están anotadas en el acta si se decide no
      crearlas.
- [ ] Nadie mantiene una lista de tareas paralela a las issues.
- [ ] No hay milestones por fase ni tablero Projects que actualizar.
