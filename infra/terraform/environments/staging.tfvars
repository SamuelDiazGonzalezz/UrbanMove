# Variables para entorno AWS Staging / Producción
is_localstack = false
aws_region    = "eu-west-1"
# aws_endpoint_url no se define para usar los endpoints estándar de AWS
# aws_access_key y secret_key se leen automáticamente del rol IAM / AWS OIDC en GitHub Actions
environment   = "staging"

datalake_bucket_name = "urbanmove-data-lake-staging-euwest1"
event_bus_name       = "urbanmove-bus-staging"
