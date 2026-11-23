# Bus de eventos de dominio, una cola SQS (+ DLQ) por consumidor y una regla de EventBridge
# que enruta a cada cola solo los eventos que le interesan (ARCHITECTURE.md §3 y §5).

locals {
  service_sources = [for s in [
    "identity", "user-profiles", "zones", "inventory", "availability", "telemetry",
    "reservations", "rides", "incidents", "maintenance", "relocation",
  ] : "urbanmove.${s}"]

  lambda_sources = ["urbanmove.mart-builder", "urbanmove.demand-relocation"]

  # detail_types vacío = todos los eventos de las fuentes indicadas.
  consumers = {
    "user-profiles-events" = {
      sources      = local.service_sources
      detail_types = ["UserRegistered"]
      delay        = 0
    }
    "availability-events" = {
      sources = local.service_sources
      detail_types = [
        "ZoneCreated", "VehiclePurchased", "VehicleRetired", "VehicleReserved", "ReservationCancelled",
        "ReservationExpired", "VehiclePickedUp", "VehicleDroppedOff", "TelemetryReceived", "VehicleBatteryLow",
        "VehicleWithdrawnForMaintenance", "VehicleChargingStarted", "VehicleRepairStarted",
        "MaintenanceOrderCompleted", "VehicleReturnedToService", "RelocationOrderCompleted",
      ]
      delay = 0
    }
    "reservations-events" = {
      sources      = local.service_sources
      detail_types = ["VehicleAvailabilityChanged", "VehicleReservationRejected", "VehiclePickedUp"]
      delay        = 0
    }
    "rides-events" = {
      sources      = local.service_sources
      detail_types = ["ZoneCreated", "VehicleAvailabilityChanged"]
      delay        = 0
    }
    "relocation-events" = {
      sources      = local.lambda_sources
      detail_types = ["DemandHotspotDetected"]
      delay        = 0
    }
    "history-events" = {
      sources      = concat(local.service_sources, local.lambda_sources)
      detail_types = []
      delay        = 0
    }
    # Colas de las Lambdas
    "analytics-ingest-events" = {
      sources      = concat(local.service_sources, local.lambda_sources)
      detail_types = []
      delay        = 0
    }
    # Sin las fuentes de las Lambdas: mart-builder no debe consumir su propio MartBuildCompleted.
    "mart-builder-events" = {
      sources      = local.service_sources
      detail_types = []
      delay        = 0
    }
    "maintenance-detection-events" = {
      sources      = local.service_sources
      detail_types = ["VehicleBatteryLow", "IncidentReported", "TripRecorded"]
      delay        = 0
    }
    # El retraso de la cola es el TTL de la reserva: el mensaje llega a la Lambda cuando vence.
    "reservation-expiration-events" = {
      sources      = local.service_sources
      detail_types = ["VehicleReserved"]
      delay        = var.reservation_ttl_seconds
    }
    "demand-relocation-events" = {
      sources      = local.lambda_sources
      detail_types = ["MartBuildCompleted"]
      delay        = 0
    }
  }
}

resource "aws_cloudwatch_event_bus" "domain" {
  name = var.event_bus_name
}

resource "aws_sqs_queue" "dlq" {
  for_each                  = local.consumers
  name                      = "${trimsuffix(each.key, "-events")}-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "consumer" {
  for_each                   = local.consumers
  name                       = each.key
  delay_seconds              = each.value.delay
  visibility_timeout_seconds = 60
  message_retention_seconds  = 345600
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = 5
  })
}

resource "aws_cloudwatch_event_rule" "consumer" {
  for_each       = local.consumers
  name           = "to-${each.key}"
  event_bus_name = aws_cloudwatch_event_bus.domain.name
  event_pattern = jsonencode(merge(
    { source = each.value.sources },
    { for k, v in { "detail-type" = each.value.detail_types } : k => v if length(v) > 0 }
  ))
}

resource "aws_cloudwatch_event_target" "consumer" {
  for_each       = local.consumers
  rule           = aws_cloudwatch_event_rule.consumer[each.key].name
  event_bus_name = aws_cloudwatch_event_bus.domain.name
  target_id      = each.key
  arn            = aws_sqs_queue.consumer[each.key].arn
}

resource "aws_sqs_queue_policy" "consumer" {
  for_each  = local.consumers
  queue_url = aws_sqs_queue.consumer[each.key].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.consumer[each.key].arn
      Condition = { ArnEquals = { "aws:SourceArn" = aws_cloudwatch_event_rule.consumer[each.key].arn } }
    }]
  })
}
