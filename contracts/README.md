# Contratos

Contratos versionados entre servicios, clientes y consumidores de eventos.

## Contenido previsto

```text
contracts/
├── openapi/       # Un YAML por microservicio
├── asyncapi/      # Catálogo de eventos del sistema
├── events/        # JSON Schema de cada evento
└── examples/      # Payloads de ejemplo para tests y demo
```

## Reglas

- **OpenAPI 3.1** para cada API REST, generado o validado en CI.
- **AsyncAPI 3** para el catálogo de eventos.
- **JSON Schema** para validar productores y consumidores.
- Versionado semántico: cambios aditivos son compatibles; los rupturistas suben
  `eventVersion`.
- Un evento no se considera terminado hasta que tiene esquema, ejemplo y test.
