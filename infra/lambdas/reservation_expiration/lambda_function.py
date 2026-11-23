"""Lambda reservation-expiration.

VehicleReserved llega a una cola SQS cuyo retraso es el TTL de la reserva. Cuando el mensaje se
hace visible, esta función pide a reservations que la expire. reservations decide: si la reserva
ya se confirmó (viaje iniciado) o se canceló, no hace nada; si sigue activa, publica
ReservationExpired y availability libera el vehículo.
"""
import json
import os
import urllib.request

RESERVATIONS_URL = os.environ.get("RESERVATIONS_URL", "http://reservations:8080")


def handler(event, context):
    for record in event.get("Records", []):
        body = json.loads(record["body"])
        envelope = body.get("detail", body)
        reservation_id = envelope["payload"]["reservationId"]
        request = urllib.request.Request(
            f"{RESERVATIONS_URL}/internal/reservations/{reservation_id}/expire",
            data=b"{}",
            method="POST",
            headers={"Content-Type": "application/json", "X-Correlation-Id": envelope.get("correlationId", "")},
        )
        with urllib.request.urlopen(request, timeout=8) as response:
            result = json.loads(response.read())
        print(f"Reserva {reservation_id}: {result.get('result')}")
    return {"statusCode": 200}
