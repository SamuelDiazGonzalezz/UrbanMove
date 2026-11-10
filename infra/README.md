# Infraestructura

Infraestructura como código y configuración del entorno local.

## Objetivos

- **Terraform** para reproducir los recursos cloud (LocalStack primero, AWS después).
- Módulos separados por responsabilidad: red, almacenamiento, mensajería, cómputo,
  observabilidad y seguridad.
- Sin secretos en el repositorio: variables sensibles por `TF_VAR_*` o gestor de secretos.
- Estado remoto cuando se use AWS real; estado local para LocalStack.

## Estructura prevista

```text
infra/
├── terraform/
│   ├── modules/
│   │   ├── network/
│   │   ├── storage/
│   │   ├── messaging/
│   │   ├── compute/
│   │   └── observability/
│   └── envs/
│       ├── local/
│       └── aws/
└── localstack/
    └── init/
```

## Entornos

| Entorno | Propósito | Coste |
|---|---|---|
| `local` | Desarrollo diario con LocalStack | 0 |
| `aws-academy` | Demostración si el aula proporciona cuenta | Créditos del aula |
| `aws` | Cuenta real, solo si existe | Controlado con presupuesto |

> No se crearán recursos en AWS hasta que el sistema funcione en local y exista una
> cuenta disponible. El enunciado permite LocalStack y Docker Compose.
