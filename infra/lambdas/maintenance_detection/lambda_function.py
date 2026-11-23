"""Lambda maintenance-detection.

Genera órdenes de mantenimiento a partir de eventos:
- VehicleBatteryLow  -> orden de carga (CHARGE).
- IncidentReported   -> orden de reparación (REPAIR), o de carga si la incidencia es de batería.
- TripRecorded       -> cuenta un uso; maintenance abre una revisión al superar el límite.
maintenance es idempotente por sourceEventId y no abre dos órdenes para el mismo vehículo.
"""
import json
import os
import urllib.request

MAINTENANCE_URL = os.environ.get("MAINTENANCE_URL", "http://maintenance:8080")


def post(path, body, correlation_id):
    request = urllib.request.Request(
        f"{MAINTENANCE_URL}{path}",
        data=json.dumps(body).encode("utf-8"),
        method="POST",
        headers={"Content-Type": "application/json", "X-Correlation-Id": correlation_id or ""},
    )
    with urllib.request.urlopen(request, timeout=8) as response:
        return json.loads(response.read())


def handler(event, context):
    for record in event.get("Records", []):
        body = json.loads(record["body"])
        envelope = body.get("detail", body)
        event_type = envelope["eventType"]
        payload = envelope["payload"]
        vehicle_id = payload["vehicleId"]
        correlation_id = envelope.get("correlationId")

        if event_type == "VehicleBatteryLow":
            result = post("/internal/maintenance-orders", {
                "vehicleId": vehicle_id,
                "type": "CHARGE",
                "reason": f"Batería baja ({payload.get('batteryPct')}%)",
                "sourceEventId": envelope["eventId"],
            }, correlation_id)
        elif event_type == "IncidentReported":
            kind = payload.get("type", "OTHER")
            description = (payload.get("description") or "").strip()
            result = post("/internal/maintenance-orders", {
                "vehicleId": vehicle_id,
                "type": "CHARGE" if kind == "BATTERY" else "REPAIR",
                "reason": f"Incidencia {kind}" + (f": {description[:120]}" if description else ""),
                "sourceEventId": envelope["eventId"],
            }, correlation_id)
        elif event_type == "TripRecorded":
            result = post(f"/internal/vehicles/{vehicle_id}/usage", {"sourceEventId": envelope["eventId"]}, correlation_id)
        else:
            continue
        print(f"{event_type} {vehicle_id}: {result.get('result')}")
    return {"statusCode": 200}
