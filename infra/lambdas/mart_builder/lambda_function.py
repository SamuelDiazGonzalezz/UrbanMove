"""Lambda mart-builder.

Consume los eventos de negocio (cola mart-builder-events), los carga como hechos validados en
analytics_db.fact_event y reconstruye los siete data marts en un build nuevo (append-only por
build_id; las vistas *_current leen el último). Publica MartBuildCompleted como mucho cada
PUBLISH_EVERY_SECONDS para que demand-relocation evalúe la regla de recolocación.
"""
import json
import os
import time
import uuid
from datetime import datetime, timezone

import boto3
import pg8000.native

EVENTS = boto3.client("events")
BUS = os.environ.get("EVENT_BUS_NAME", "urbanmove-bus")
MIN_AVAILABLE = int(os.environ.get("DEMAND_MIN_AVAILABLE", "3"))
MIN_EVENTS = int(os.environ.get("DEMAND_MIN_EVENTS", "4"))
COOLDOWN_MINUTES = int(os.environ.get("DEMAND_COOLDOWN_MINUTES", "10"))
PUBLISH_EVERY_SECONDS = 20
KEEP_BUILDS = 20

MARTS = [
    # mart_flota: último estado conocido de cada vehículo, por zona, tipo y estado.
    """
    INSERT INTO mart_flota (build_id, zone_id, vehicle_type, state, vehicles)
    SELECT CAST(:b AS uuid), zone_id, vehicle_type, state, count(*) FROM (
        SELECT DISTINCT ON (vehicle_id) vehicle_id, payload->>'zoneId' AS zone_id,
               payload->>'vehicleType' AS vehicle_type, payload->>'toState' AS state
        FROM fact_event WHERE event_type = 'VehicleAvailabilityChanged'
        ORDER BY vehicle_id, occurred_at DESC) v
    GROUP BY zone_id, vehicle_type, state
    """,
    # mart_reservas: zona / día / hora.
    """
    INSERT INTO mart_reservas (build_id, date_key, hour, zone_id, reserved, cancelled, expired, completed, rejected)
    SELECT CAST(:b AS uuid), occurred_at::date, extract(hour FROM occurred_at)::smallint, coalesce(zone_id, 'desconocida'),
           count(*) FILTER (WHERE event_type = 'VehicleReserved'),
           count(*) FILTER (WHERE event_type = 'ReservationCancelled'),
           count(*) FILTER (WHERE event_type = 'ReservationExpired'),
           count(*) FILTER (WHERE event_type = 'ReservationCompleted'),
           count(*) FILTER (WHERE event_type = 'ReservationRejected')
    FROM fact_event
    WHERE event_type IN ('VehicleReserved', 'ReservationCancelled', 'ReservationExpired', 'ReservationCompleted', 'ReservationRejected')
      AND occurred_at > now() - interval '7 days'
    GROUP BY 2, 3, 4
    """,
    # mart_viajes: origen-destino / hora / tipo.
    """
    INSERT INTO mart_viajes (build_id, date_key, hour, origin_zone_id, destination_zone_id, vehicle_type, trips,
                             avg_duration_seconds, total_distance_meters, revenue)
    SELECT CAST(:b AS uuid), occurred_at::date, extract(hour FROM occurred_at)::smallint,
           payload->'origin'->>'zoneId', payload->'destination'->>'zoneId', payload->>'vehicleType', count(*),
           round(avg((payload->>'durationSeconds')::numeric), 1), sum((payload->>'distanceMeters')::bigint),
           sum((payload->>'preliminaryCost')::numeric)
    FROM fact_event WHERE event_type = 'TripRecorded' AND occurred_at > now() - interval '7 days'
    GROUP BY 2, 3, 4, 5, 6
    """,
    # mart_demanda: disponibilidad actual frente a demanda de la última hora (regla de recolocación).
    """
    WITH zones AS (
        SELECT DISTINCT ON (payload->>'zoneId') payload->>'zoneId' AS zone_id, payload->>'name' AS zone_name,
               (payload->>'targetVehicles')::int AS target
        FROM fact_event WHERE event_type = 'ZoneCreated' ORDER BY payload->>'zoneId', occurred_at DESC),
    avail AS (
        SELECT zone_id, sum(vehicles) FILTER (WHERE state = 'AVAILABLE') AS available
        FROM mart_flota WHERE build_id = CAST(:b AS uuid) GROUP BY zone_id),
    recent AS (
        SELECT zone_id,
               count(*) FILTER (WHERE event_type = 'VehicleReserved') AS res,
               count(*) FILTER (WHERE event_type = 'ReservationRejected') AS rej,
               count(*) FILTER (WHERE event_type = 'VehicleAvailabilityChanged' AND payload->>'toState' = 'IN_USE') AS rides
        FROM fact_event
        WHERE occurred_at > now() - interval '1 hour'
          AND event_type IN ('VehicleReserved', 'ReservationRejected', 'VehicleAvailabilityChanged')
        GROUP BY zone_id)
    INSERT INTO mart_demanda (build_id, zone_id, zone_name, target_vehicles, available_now, reservations_last_hour,
                              rejections_last_hour, rides_started_last_hour, demand_last_hour, balance, status)
    SELECT CAST(:b AS uuid), z.zone_id, z.zone_name, z.target, coalesce(a.available, 0),
           coalesce(r.res, 0), coalesce(r.rej, 0), coalesce(r.rides, 0),
           coalesce(r.res, 0) + coalesce(r.rej, 0) + coalesce(r.rides, 0),
           coalesce(a.available, 0) - coalesce(z.target, 0),
           CASE
               WHEN coalesce(a.available, 0) < :min_av
                    AND coalesce(r.res, 0) + coalesce(r.rej, 0) + coalesce(r.rides, 0) > :min_ev THEN 'DEFICIT'
               WHEN coalesce(a.available, 0) > :min_av THEN 'EXCESS'
               ELSE 'OK'
           END
    FROM zones z LEFT JOIN avail a USING (zone_id) LEFT JOIN recent r USING (zone_id)
    """,
    # mart_mantenimiento: día.
    """
    WITH out_time AS (
        SELECT r.occurred_at::date AS date_key, avg(extract(epoch FROM r.occurred_at - w.occurred_at) / 60) AS mins
        FROM fact_event r
        JOIN fact_event w ON w.event_type = 'VehicleWithdrawnForMaintenance' AND w.payload->>'orderId' = r.payload->>'orderId'
        WHERE r.event_type = 'VehicleReturnedToService'
        GROUP BY 1)
    INSERT INTO mart_mantenimiento (build_id, date_key, battery_low, incidents, orders_opened, orders_completed, returned, avg_minutes_out)
    SELECT CAST(:b AS uuid), d.date_key, d.battery_low, d.incidents, d.opened, d.completed, d.returned, round(o.mins::numeric, 1)
    FROM (
        SELECT occurred_at::date AS date_key,
               count(*) FILTER (WHERE event_type = 'VehicleBatteryLow') AS battery_low,
               count(*) FILTER (WHERE event_type = 'IncidentReported') AS incidents,
               count(*) FILTER (WHERE event_type = 'MaintenanceOrderOpened') AS opened,
               count(*) FILTER (WHERE event_type = 'MaintenanceOrderCompleted') AS completed,
               count(*) FILTER (WHERE event_type = 'VehicleReturnedToService') AS returned
        FROM fact_event
        WHERE event_type IN ('VehicleBatteryLow', 'IncidentReported', 'MaintenanceOrderOpened',
                             'MaintenanceOrderCompleted', 'VehicleReturnedToService')
          AND occurred_at > now() - interval '30 days'
        GROUP BY 1) d
    LEFT JOIN out_time o USING (date_key)
    """,
    # mart_operaciones: día / zona destino.
    """
    INSERT INTO mart_operaciones (build_id, date_key, to_zone_id, orders_created, orders_completed, vehicles_moved, avg_minutes_to_complete)
    SELECT CAST(:b AS uuid), occurred_at::date, payload->>'toZoneId',
           count(*) FILTER (WHERE event_type = 'RelocationOrderCreated'),
           count(*) FILTER (WHERE event_type = 'RelocationOrderCompleted'),
           coalesce(sum((payload->>'quantityMoved')::int) FILTER (WHERE event_type = 'RelocationOrderCompleted'), 0),
           round(avg((payload->>'minutesToComplete')::numeric) FILTER (WHERE event_type = 'RelocationOrderCompleted'), 1)
    FROM fact_event WHERE event_type IN ('RelocationOrderCreated', 'RelocationOrderCompleted')
    GROUP BY 2, 3
    """,
    # mart_usuarios: día.
    """
    INSERT INTO mart_usuarios (build_id, date_key, active_users, new_users, trips, cancellations)
    SELECT CAST(:b AS uuid), occurred_at::date,
           count(DISTINCT user_id) FILTER (WHERE event_type IN ('VehicleReserved', 'VehiclePickedUp', 'TripRecorded')),
           count(*) FILTER (WHERE event_type = 'UserRegistered'),
           count(*) FILTER (WHERE event_type = 'TripRecorded'),
           count(*) FILTER (WHERE event_type = 'ReservationCancelled')
    FROM fact_event WHERE occurred_at > now() - interval '30 days'
    GROUP BY 2
    """,
]


def connect():
    return pg8000.native.Connection(
        user=os.environ["DB_USER"], password=os.environ["DB_PASSWORD"], host=os.environ.get("DB_HOST", "postgres"),
        port=int(os.environ.get("DB_PORT", "5432")), database=os.environ.get("DB_NAME", "analytics_db"), timeout=15)


def envelope_of(record):
    body = json.loads(record.get("body") or "{}")
    detail = body.get("detail", body)
    return json.loads(detail) if isinstance(detail, str) else detail


def zone_of(payload):
    return payload.get("zoneId") or (payload.get("destination") or {}).get("zoneId") or payload.get("toZoneId")


def load_facts(con, records):
    loaded = 0
    for record in records:
        e = envelope_of(record)
        if not e.get("eventId") or not e.get("eventType") or not e.get("occurredAt"):
            continue  # no cumple el sobre: analytics-ingest lo deja en quarantine/
        payload = e.get("payload") or {}
        con.run("""
            INSERT INTO fact_event (event_id, event_type, occurred_at, producer, correlation_id, user_id, vehicle_id, zone_id, payload)
            VALUES (CAST(:id AS uuid), :type, CAST(:occ AS timestamptz), :producer, :corr, :uid, :vid, :zid, CAST(:payload AS jsonb))
            ON CONFLICT (event_id) DO NOTHING
            """, id=e["eventId"], type=e["eventType"], occ=e["occurredAt"], producer=e.get("producer", "unknown"),
                corr=e.get("correlationId"), uid=payload.get("userId"), vid=payload.get("vehicleId"), zid=zone_of(payload),
                payload=json.dumps(payload))
        loaded += 1
    return loaded


def build(con):
    started = time.time()
    build_id = str(uuid.uuid4())
    con.run("START TRANSACTION")
    try:
        con.run("INSERT INTO mart_build (build_id, facts_count, duration_ms) VALUES (CAST(:b AS uuid), 0, 0)", b=build_id)
        for sql in MARTS:
            params = {"b": build_id}
            if ":min_av" in sql:
                params.update(min_av=MIN_AVAILABLE, min_ev=MIN_EVENTS)
            con.run(sql, **params)
        facts = con.run("SELECT count(*) FROM fact_event")[0][0]
        con.run("UPDATE mart_build SET facts_count = :f, duration_ms = :d WHERE build_id = CAST(:b AS uuid)",
                f=facts, d=int((time.time() - started) * 1000), b=build_id)
        con.run("DELETE FROM mart_build WHERE build_id IN (SELECT build_id FROM mart_build ORDER BY built_at DESC OFFSET :k)",
                k=KEEP_BUILDS)
        con.run("COMMIT")
    except Exception:
        con.run("ROLLBACK")
        raise
    return build_id, facts


def publish_if_due(con, build_id, facts):
    recent = con.run("SELECT count(*) FROM mart_build WHERE published AND built_at > now() - make_interval(secs => :s)",
                     s=PUBLISH_EVERY_SECONDS)[0][0]
    # Una zona en déficit que aún no se ha señalado no puede esperar al siguiente evento.
    new_deficit = con.run("""
        SELECT count(*) FROM mart_demanda d
        WHERE d.build_id = CAST(:b AS uuid) AND d.status = 'DEFICIT'
          AND NOT EXISTS (SELECT 1 FROM demand_signal s WHERE s.zone_id = d.zone_id
                          AND s.last_detected_at > now() - make_interval(mins => :m))
        """, b=build_id, m=COOLDOWN_MINUTES)[0][0]
    if recent and not new_deficit:
        return False
    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    envelope = {
        "eventId": str(uuid.uuid4()), "eventType": "MartBuildCompleted", "eventVersion": 1,
        "schema": "https://urbanmove.local/schemas/MartBuildCompleted.v1.json",
        "occurredAt": now, "recordedAt": now, "source": "mart-builder", "producer": "mart-builder",
        "correlationId": str(uuid.uuid4()), "causationId": None,
        "payload": {"buildId": build_id, "factsCount": facts,
                    "marts": ["mart_flota", "mart_reservas", "mart_viajes", "mart_demanda",
                              "mart_mantenimiento", "mart_operaciones", "mart_usuarios"]},
    }
    EVENTS.put_events(Entries=[{"Source": "urbanmove.mart-builder", "DetailType": "MartBuildCompleted",
                                "Detail": json.dumps(envelope), "EventBusName": BUS}])
    con.run("UPDATE mart_build SET published = TRUE WHERE build_id = CAST(:b AS uuid)", b=build_id)
    return True


def handler(event, context):
    con = connect()
    try:
        loaded = load_facts(con, event.get("Records", []))
        build_id, facts = build(con)
        published = publish_if_due(con, build_id, facts)
        print(f"Build {build_id}: {loaded} hechos nuevos, {facts} en total, publicado={published}")
    finally:
        con.close()
    return {"statusCode": 200}
