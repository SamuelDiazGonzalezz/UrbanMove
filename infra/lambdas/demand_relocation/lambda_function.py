"""Lambda demand-relocation.

Se dispara con MartBuildCompleted. Lee mart_demanda_current y aplica la regla del enunciado
(§8.3): una zona con menos de DEMAND_MIN_AVAILABLE vehículos disponibles y más de
DEMAND_MIN_EVENTS intentos de reserva o viajes iniciados en la última hora está en déficit.
Publica DemandHotspotDetected con la zona de origen con más exceso; relocation crea la orden.
Un enfriamiento por zona evita repetir la señal mientras el déficit persiste.
"""
import json
import os
import uuid
from datetime import datetime, timezone

import boto3
import pg8000.native

EVENTS = boto3.client("events")
BUS = os.environ.get("EVENT_BUS_NAME", "urbanmove-bus")
MIN_AVAILABLE = int(os.environ.get("DEMAND_MIN_AVAILABLE", "3"))
MIN_EVENTS = int(os.environ.get("DEMAND_MIN_EVENTS", "4"))
COOLDOWN_MINUTES = int(os.environ.get("DEMAND_COOLDOWN_MINUTES", "10"))


def connect():
    return pg8000.native.Connection(
        user=os.environ["DB_USER"], password=os.environ["DB_PASSWORD"], host=os.environ.get("DB_HOST", "postgres"),
        port=int(os.environ.get("DB_PORT", "5432")), database=os.environ.get("DB_NAME", "analytics_db"), timeout=15)


def rows(con, sql, **params):
    result = con.run(sql, **params)
    names = [c["name"] for c in con.columns]
    return [dict(zip(names, r)) for r in result]


def publish(payload, correlation_id):
    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    envelope = {
        "eventId": str(uuid.uuid4()), "eventType": "DemandHotspotDetected", "eventVersion": 1,
        "schema": "https://urbanmove.local/schemas/DemandHotspotDetected.v1.json",
        "occurredAt": now, "recordedAt": now, "source": "zone-" + payload["zoneId"], "producer": "demand-relocation",
        "correlationId": correlation_id or str(uuid.uuid4()), "causationId": None, "payload": payload,
    }
    EVENTS.put_events(Entries=[{"Source": "urbanmove.demand-relocation", "DetailType": "DemandHotspotDetected",
                                "Detail": json.dumps(envelope), "EventBusName": BUS}])


def handler(event, context):
    correlation_id = None
    for record in event.get("Records", []):
        body = json.loads(record["body"])
        correlation_id = (body.get("detail") or {}).get("correlationId")

    con = connect()
    try:
        demand = rows(con, "SELECT * FROM mart_demanda_current")
        deficits = [z for z in demand if z["status"] == "DEFICIT"]
        for zone in deficits:
            cooling = con.run("""
                SELECT count(*) FROM demand_signal
                WHERE zone_id = :z AND last_detected_at > now() - make_interval(mins => :m)
                """, z=zone["zone_id"], m=COOLDOWN_MINUTES)[0][0]
            if cooling:
                continue
            sources = sorted((z for z in demand if z["zone_id"] != zone["zone_id"] and z["available_now"] > MIN_AVAILABLE),
                             key=lambda z: z["available_now"], reverse=True)
            if not sources:
                print(f"Déficit en {zone['zone_id']} pero ninguna zona tiene exceso")
                continue
            source = sources[0]
            quantity = max(1, min(MIN_AVAILABLE - zone["available_now"], source["available_now"] - MIN_AVAILABLE))
            publish({
                "zoneId": zone["zone_id"],
                "zoneName": zone["zone_name"],
                "available": zone["available_now"],
                "demandLastHour": zone["demand_last_hour"],
                "sourceZoneId": source["zone_id"],
                "sourceAvailable": source["available_now"],
                "suggestedQuantity": quantity,
                "rule": f"disponibles < {MIN_AVAILABLE} y demanda última hora > {MIN_EVENTS}",
            }, correlation_id)
            con.run("""
                INSERT INTO demand_signal (zone_id, last_detected_at) VALUES (:z, now())
                ON CONFLICT (zone_id) DO UPDATE SET last_detected_at = now()
                """, z=zone["zone_id"])
            print(f"DemandHotspotDetected {zone['zone_id']} <- {source['zone_id']} x{quantity}")
        if not deficits:
            print("Sin zonas en déficit")
    finally:
        con.close()
    return {"statusCode": 200}
