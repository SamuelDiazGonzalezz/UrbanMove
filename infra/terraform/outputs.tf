output "datalake_bucket_name" {
  description = "Bucket S3 del Data Lake (raw/, trusted/, quarantine/)"
  value       = aws_s3_bucket.datalake.bucket
}

output "event_bus_name" {
  description = "Bus de eventos de dominio"
  value       = aws_cloudwatch_event_bus.domain.name
}

output "consumer_queues" {
  description = "Colas SQS por consumidor"
  value       = { for k, q in aws_sqs_queue.consumer : k => q.id }
}

output "lambdas" {
  description = "Lambdas desplegadas"
  value       = [for f in aws_lambda_function.fn : f.function_name]
}
