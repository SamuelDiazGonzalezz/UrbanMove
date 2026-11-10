# Funciones Lambda

Funciones serverless equivalentes a las exigidas por el enunciado.

| Lambda | Disparador | Responsabilidad | Obligatoria |
|---|---|---|---|
| `reservation-expiration` | EventBridge Scheduler (cada minuto) | Libera reservas no confirmadas y emite `ReservationExpired` | Sí |
| `maintenance-detection` | Evento de telemetría | Detecta batería baja o exceso de usos y crea orden de mantenimiento | Sí |
| `analytics-ingest` | Evento de dominio | Valida y escribe el evento en S3 raw | Sí |
| `mart-builder` | EventBridge Scheduler | Construye trusted, curated y actualiza data marts | Ampliación |
| `demand-relocation` | EventBridge Scheduler | Detecta déficit y crea órdenes de recolocación | Ampliación |

## Reglas

- Las funciones deben ser idempotentes.
- Deben registrar `correlationId` y `eventId`.
- Los fallos irrecuperables van a DLQ o a una zona de cuarentena en S3.
- El código y el empaquetado deben ser reproducibles (`mvn package` o `pip install -r`).
- En local se ejecutan contra LocalStack; en AWS, contra los servicios reales.
