output "datalake_bucket_arn" {
  description = "ARN del bucket S3 del Data Lake"
  value       = aws_s3_bucket.datalake.arn
}

output "datalake_bucket_name" {
  description = "Nombre del bucket S3 del Data Lake"
  value       = aws_s3_bucket.datalake.bucket
}

output "trip_recorded_queue_url" {
  description = "URL de la cola SQS de eventos de viaje"
  value       = aws_sqs_queue.trip_recorded_queue.id
}

output "trip_recorded_queue_arn" {
  description = "ARN de la cola SQS de eventos de viaje"
  value       = aws_sqs_queue.trip_recorded_queue.arn
}

output "lambda_analytics_ingest_arn" {
  description = "ARN de la función Lambda de ingesta"
  value       = aws_lambda_function.analytics_ingest.arn
}
