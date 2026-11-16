# 0011. Infraestructura como Código (IaC) con Terraform en LocalStack

- **Estado:** Aceptada / Implementada
- **Fecha:** 2026-11-16
- **Decisores:** Equipo UrbanMove

## Contexto

El aprovisionamiento de recursos cloud simulados (bucket S3, colas SQS, DLQ, rol IAM, empaquetado de Lambda y Event Source Mapping) se realizaba inicialmente mediante scripts bash imperativos (`init-aws.sh`).

Para garantizar reproducibilidad, trazabilidad de cambios, gestión de estado y permitir la migración directa a AWS real o AWS Academy sin reescribir la infraestructura, se adopta Terraform como herramienta declarativa de Infraestructura como Código (IaC).

## Decisión

1. **Estructura Declarativa en `infra/terraform/`:**
   - `main.tf`: Configuración del provider oficial `hashicorp/aws` condicionado para LocalStack (`endpoints { s3 = ... }`).
   - `variables.tf`: Parametrización de región, nombres de buckets y endpoints.
   - `s3.tf`: Bucket `urbanmove-data-lake` con versionado habilitado.
   - `sqs.tf`: Colas `trip-recorded-queue`, `vehicle-telemetry-queue` y `urbanmove-dlq` (Dead Letter Queue con política de reenvío automático).
   - `lambda.tf`: Empaquetado dinámico de `analytics_ingest` en zip, creación de función en Python 3.11 y trigger `aws_lambda_event_source_mapping` desde SQS.
   - `outputs.tf`: ARNs y URLs para consumo por los microservicios.

2. **Gestión Segura de Secretos y Parámetros (`.env`):**
   - Todas las credenciales y variables se configuran mediante el mecanismo nativo `TF_VAR_*` en `.env` (ignorado por Git).
   - Plantilla documentada y pública en `.env.example`.

## Consecuencias y Evidencias

- **Ejecución exitosa de `terraform apply`:** 8 recursos creados de forma atómica y consistente.
- **Portabilidad Cloud:** Para desplegar en AWS real, únicamente se cambia `TF_VAR_is_localstack=false` y las credenciales reales de AWS en `.env`.
