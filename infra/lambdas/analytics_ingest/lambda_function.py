"""Lambda analytics-ingest: copia cada evento del bus al Data Lake.

raw/        evento tal cual llega (JSON), particionado por productor, tipo y hora.
trusted/    evento validado contra el sobre y normalizado (JSON; Parquet queda como mejora).
quarantine/ eventos que no cumplen el sobre canónico.
"""
import json
import os
from datetime import datetime, timezone

import boto3

s3 = boto3.client("s3")
BUCKET = os.environ.get("S3_DATALAKE_BUCKET", "urbanmove-data-lake")
REQUIRED = ("eventId", "eventType", "eventVersion", "schema", "occurredAt", "source", "producer", "correlationId", "payload")


def envelope_of(record):
    body = json.loads(record.get("body") or "{}")
    detail = body.get("detail", body)
    return json.loads(detail) if isinstance(detail, str) else detail


def occurred(envelope):
    try:
        return datetime.fromisoformat(envelope["occurredAt"].replace("Z", "+00:00"))
    except Exception:  # noqa: BLE001 - un evento sin fecha válida va a cuarentena
        return datetime.now(timezone.utc)


def zone_of(payload):
    return payload.get("zoneId") or (payload.get("destination") or {}).get("zoneId") or payload.get("toZoneId")


def put(key, data):
    s3.put_object(Bucket=BUCKET, Key=key, Body=json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8"),
                  ContentType="application/json")


def handler(event, context):
    for record in event.get("Records", []):
        envelope = envelope_of(record)
        event_id = envelope.get("eventId", record.get("messageId"))
        event_type = envelope.get("eventType", "Unknown")
        producer = envelope.get("producer", "unknown")
        ts = occurred(envelope)

        put(f"raw/source={producer}/event_type={event_type}/year={ts:%Y}/month={ts:%m}/day={ts:%d}/hour={ts:%H}/{event_id}.json",
            envelope)

        missing = [f for f in REQUIRED if envelope.get(f) in (None, "")]
        if missing:
            put(f"quarantine/event_type={event_type}/year={ts:%Y}/month={ts:%m}/day={ts:%d}/{event_id}.json",
                {"reason": f"faltan campos: {', '.join(missing)}", "event": envelope})
            print(f"Cuarentena {event_id}: faltan {missing}")
            continue

        payload = envelope["payload"] or {}
        trusted = {
            "eventId": event_id,
            "eventType": event_type,
            "eventVersion": envelope["eventVersion"],
            "occurredAt": ts.isoformat(),
            "producer": producer,
            "correlationId": envelope["correlationId"],
            "userId": payload.get("userId"),
            "vehicleId": payload.get("vehicleId"),
            "zoneId": zone_of(payload),
            "payload": payload,
        }
        put(f"trusted/event_type={event_type}/year={ts:%Y}/month={ts:%m}/day={ts:%d}/{event_id}.json", trusted)
        print(f"Ingestado {event_type} {event_id}")
    return {"statusCode": 200}
