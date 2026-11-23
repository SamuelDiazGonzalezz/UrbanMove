# Cinco Lambdas conectadas a eventos reales (enunciado §4.3). Cada una lee su cola SQS.
# El código (y las dependencias de las que usan PostgreSQL) lo empaqueta infra/lambdas/build.sh
# en infra/lambdas/build/<nombre>.zip antes de aplicar Terraform (servicio lambda-build).

locals {
  lambdas = {
    "analytics-ingest" = {
      dir     = "analytics_ingest"
      queue   = "analytics-ingest-events"
      timeout = 30
      batch   = 10
    }
    "reservation-expiration" = {
      dir     = "reservation_expiration"
      queue   = "reservation-expiration-events"
      timeout = 20
      batch   = 5
    }
    "maintenance-detection" = {
      dir     = "maintenance_detection"
      queue   = "maintenance-detection-events"
      timeout = 20
      batch   = 5
    }
    "mart-builder" = {
      dir     = "mart_builder"
      queue   = "mart-builder-events"
      timeout = 45
      batch   = 10
    }
    "demand-relocation" = {
      dir     = "demand_relocation"
      queue   = "demand-relocation-events"
      timeout = 30
      batch   = 1
    }
  }

  lambda_env = {
    S3_DATALAKE_BUCKET      = aws_s3_bucket.datalake.bucket
    EVENT_BUS_NAME          = aws_cloudwatch_event_bus.domain.name
    RESERVATIONS_URL        = "http://reservations:8080"
    MAINTENANCE_URL         = "http://maintenance:8080"
    DB_HOST                 = "postgres"
    DB_PORT                 = "5432"
    DB_NAME                 = "analytics_db"
    DB_USER                 = var.db_user
    DB_PASSWORD             = var.db_password
    DEMAND_MIN_AVAILABLE    = tostring(var.demand_min_available)
    DEMAND_MIN_EVENTS       = tostring(var.demand_min_events)
    DEMAND_COOLDOWN_MINUTES = "10"
  }
}

locals {
  lambda_zip = { for k, v in local.lambdas : k => "${path.module}/../lambdas/build/${v.dir}.zip" }
}

resource "aws_iam_role" "lambda_exec_role" {
  name = "urbanmove-lambda-exec-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_lambda_function" "fn" {
  for_each         = local.lambdas
  function_name    = each.key
  filename         = local.lambda_zip[each.key]
  source_code_hash = filebase64sha256(local.lambda_zip[each.key])
  role             = aws_iam_role.lambda_exec_role.arn
  handler          = "lambda_function.handler"
  runtime          = "python3.11"
  timeout          = each.value.timeout
  memory_size      = 256

  environment {
    variables = local.lambda_env
  }

  tags = {
    Environment = var.environment
    Project     = "UrbanMove"
    ManagedBy   = "Terraform"
  }
}

resource "aws_lambda_event_source_mapping" "fn" {
  for_each         = local.lambdas
  event_source_arn = aws_sqs_queue.consumer[each.value.queue].arn
  function_name    = aws_lambda_function.fn[each.key].arn
  batch_size       = each.value.batch
}
