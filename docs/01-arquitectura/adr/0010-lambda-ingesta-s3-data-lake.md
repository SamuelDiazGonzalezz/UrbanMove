# 0010. Pipeline de Eventos Cloud: Ingesta Serverless con Lambda a S3 Data Lake (Medallion raw/)

- **Estado:** Aceptada / Implementada
- **Fecha:** 2026-11-16
- **Decisores:** Equipo UrbanMove

## Contexto

El enunciado y la arquitectura de datos (`docs/01-arquitectura/DATA-ARCHITECTURE.md`) exigen un Data Lake inmutable en S3 organizado por capas (Kappa / Medallion: `raw/`, `trusted/`, `curated/`) alimentado a través de eventos asíncronos. Para evitar acoplar el microservicio operacional directamente con el guardado de ficheros en S3, se desacopla mediante colas y cómputo serverless (AWS Lambda).

## Decisión

1. **Flujo de Viajes Completo en Backend (`rides`):**
   - Casos de uso `startRide(reservationId)` (vehículo pasa a `IN_USE`) y `finishRide(rideId, endLat, endLon)` (vehículo vuelve a `AVAILABLE` con nueva posición y consumo de batería).
   - Generación y publicación del evento de dominio `TripRecordedEvent` hacia SQS.

2. **Cómputo Serverless: Lambda `analytics-ingest` en Python:**
   - Ubicación: `infra/lambdas/analytics_ingest/lambda_function.py`.
   - Event Source Mapping en LocalStack: SQS (`trip-recorded-queue`) invoca automáticamente la Lambda.
   - La función deserializa el evento y lo particiona por fecha y tipo en S3:
     `s3://urbanmove-data-lake/raw/{trips|reservations}/year=YYYY/month=MM/day=DD/{eventId}.json`
   - Los datos en `raw/` son 100% inmutables y auditables.

3. **Panel de Control / Observabilidad en Tiempo Real:**
   - `AdminController` exponiendo endpoints REST:
     - `GET /api/v1/admin/metrics`: Agregados en vivo (vehículos en uso, total viajes, recaudación acumulada).
     - `GET /api/v1/admin/datalake/objects`: Inspección de objetos en el bucket S3 en tiempo real.
   - Pestaña "Panel Cloud & S3 Data Lake" integrada en `index.html`.

## Consecuencias y Evidencias

- Eventos `ReservationCreatedEvent` y `TripRecordedEvent` ingeridos automáticamente en `s3://urbanmove-data-lake/raw/`.
- Verificado el contenido del objeto en S3 con metadata completa (ID de viaje, código de vehículo, usuario, distancia y coste).
