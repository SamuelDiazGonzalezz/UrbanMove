# Cola de eventos de viajes (TripRecorded y ReservationCreated)
resource "aws_sqs_queue" "trip_recorded_queue" {
  name                      = var.trip_recorded_queue_name
  message_retention_seconds = 86400
  visibility_timeout_seconds = 30

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dead_letter_queue.arn
    maxReceiveCount     = 3
  })

  tags = {
    Environment = var.environment
    Project     = "UrbanMove"
    ManagedBy   = "Terraform"
  }
}

# Cola para telemetría
resource "aws_sqs_queue" "telemetry_queue" {
  name                      = "vehicle-telemetry-queue"
  message_retention_seconds = 86400

  tags = {
    Environment = var.environment
    Project     = "UrbanMove"
    ManagedBy   = "Terraform"
  }
}

# Dead Letter Queue (DLQ)
resource "aws_sqs_queue" "dead_letter_queue" {
  name                      = "urbanmove-dlq"
  message_retention_seconds = 1209600 # 14 días

  tags = {
    Environment = var.environment
    Project     = "UrbanMove"
    ManagedBy   = "Terraform"
  }
}
