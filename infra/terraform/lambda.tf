# Empaquetar la función Python en un .zip dinámicamente
data "archive_file" "analytics_ingest_zip" {
  type        = "zip"
  source_file = "${path.module}/../lambdas/analytics_ingest/lambda_function.py"
  output_path = "${path.module}/analytics_ingest.zip"
}

# Rol de IAM para la Lambda
resource "aws_iam_role" "lambda_exec_role" {
  name = "urbanmove-lambda-exec-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# Función Lambda de Ingesta al Data Lake
resource "aws_lambda_function" "analytics_ingest" {
  function_name    = "analytics-ingest"
  filename         = data.archive_file.analytics_ingest_zip.output_path
  source_code_hash = data.archive_file.analytics_ingest_zip.output_base64sha256
  role             = aws_iam_role.lambda_exec_role.arn
  handler          = "lambda_function.handler"
  runtime          = "python3.11"
  timeout          = 10

  environment {
    variables = {
      S3_DATALAKE_BUCKET = aws_s3_bucket.datalake.bucket
    }
  }

  tags = {
    Environment = var.environment
    Project     = "UrbanMove"
    ManagedBy   = "Terraform"
  }
}

# Event Source Mapping: Trigger automático SQS -> Lambda
resource "aws_lambda_event_source_mapping" "sqs_trigger" {
  event_source_arn = aws_sqs_queue.trip_recorded_queue.arn
  function_name    = aws_lambda_function.analytics_ingest.arn
  batch_size       = 10
}
