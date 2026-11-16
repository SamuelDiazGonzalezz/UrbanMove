# Manual de usuario y pruebas (MVP Fase 0 - UrbanMove)

> **Ámbito:** entorno local del MVP actual (`core-fleet`) sobre Docker Compose + PostgreSQL + LocalStack + Terraform.  
> **Objetivo:** levantar el sistema, ejecutar el flujo funcional end-to-end y verificar que los eventos llegan a S3 mediante SQS + Lambda gestionados por Terraform.  
> **Última actualización:** 2026-11-16 (Fase 0 - IaC & Secretos integrados).

---

## 1. Qué incluye este MVP

- **Infraestructura local:** PostgreSQL 16 y LocalStack 3.0 (S3, SQS, SNS, Lambda, IAM).
- **Gestión de Secretos y Configuración:**
  - Archivo `.env` local (ignorado por Git) para contraseñas de BD y puertos.
  - Plantilla pública documentada [`.env.example`](../../.env.example).
- **Infraestructura como Código (IaC):**
  - Directorio `infra/terraform/` gestiona declarativamente el bucket S3 (con versionado), las colas SQS (incluyendo DLQ), el rol IAM, la Lambda en Python y el trigger SQS ➔ Lambda.
- **Servicio:** `core-fleet` (Spring Boot, Java 21 en Arquitectura Hexagonal) con:
  - Consulta de flota y reservas en tiempo real.
  - Inicio y finalización de viajes (cálculo de coste y distancia).
  - Publicación de eventos de dominio en SQS (`ReservationCreatedEvent`, `TripRecordedEvent`).
  - Panel y endpoints administrativos para métricas en vivo y visor de objetos del Data Lake S3.
  - Interfaz web interactiva en `/` con mapa Leaflet.js y Tailwind CSS.
- **Flujo asíncrono Data Lake:** Publicación en SQS → Lambda `analytics-ingest` → almacenamiento inmutable en `s3://urbanmove-data-lake/raw/{trips|reservations}/year=YYYY/month=MM/day=DD/{eventId}.json`.
- **Datos semilla:** flota inicial de Sítycleta (Las Palmas de Gran Canaria: San Telmo, Santa Catalina, Plaza de la Feria, etc.) cargada automáticamente por Flyway.

---

## 2. Prerrequisitos

| Requisito | Detalle |
|---|---|
| Docker Desktop / Docker Engine + Compose | Necesario para levantar contenedores |
| Terraform (>= 1.5.0) | Necesario para aplicar IaC sobre LocalStack |
| Puertos libres en host | `5432` (PostgreSQL), `4566` (LocalStack), `8080` (`core-fleet`) |
| RAM/CPU recomendada | Al menos 8 GB de RAM asignados a Docker |

---

## 3. Guía de Arranque Paso a Paso

### Paso 1: Configurar variables de entorno
Si clonas el repositorio por primera vez, copia la plantilla `.env.example`:
```bash
cp .env.example .env
```
*(En Windows PowerShell: `Copy-Item .env.example .env`)*

### Paso 2: Levantar el stack con Docker Compose
Desde la raíz del repositorio:
```bash
# Arranque en segundo plano
docker compose up -d --build
```
Verifica que los contenedores estén en estado `healthy` o `Up`:
```bash
docker compose ps
```

### Paso 3: Aprovisionar recursos Cloud con Terraform
Una vez que LocalStack esté en estado `healthy`, aplica la infraestructura declarativa:
```bash
cd infra/terraform
terraform init
terraform apply -auto-approve
cd ../..
```
*(Terraform leerá automáticamente las variables `TF_VAR_*` configuradas en tu `.env`)*

---

## 4. Acceso a la Interfaz Web y Endpoints

| Recurso | URL | Descripción |
|---|---|---|
| **UI Web Completa** | `http://localhost:8080/` | Interfaz de usuario (mapa) y panel de administración |
| API - Vehículos | `GET http://localhost:8080/api/v1/vehicles` | Lista completa de vehículos |
| API - Solo disponibles | `GET http://localhost:8080/api/v1/vehicles?availableOnly=true` | Filtra solo vehículos disponibles |
| API - Crear reserva | `POST http://localhost:8080/api/v1/reservations` | Bloquea vehículo y genera reserva |
| API - Iniciar viaje | `POST http://localhost:8080/api/v1/rides/start` | Pasa vehículo a `IN_USE` |
| API - Finalizar viaje | `POST http://localhost:8080/api/v1/rides/{rideId}/finish` | Finaliza viaje, calcula tarifa y publica a SQS |
| API - Histórico viajes | `GET http://localhost:8080/api/v1/rides` | Listado de viajes realizados |
| Admin - Métricas en vivo | `GET http://localhost:8080/api/v1/admin/metrics` | Viajes totales, ingresos acumulados, flota |
| Admin - Objetos en S3 | `GET http://localhost:8080/api/v1/admin/datalake/objects` | Ficheros raw en el Data Lake |

---

## 5. Prueba Guiada E2E (Desde la Interfaz Web)

1. Abre en tu navegador: **`http://localhost:8080/`**
2. **Pestaña "🛴 Mapa & Usuario":**
   - Verás el mapa interactivo centrado en Las Palmas de Gran Canaria con las estaciones de Sítycleta.
   - Haz clic en **"Reservar"** sobre una bicicleta disponible (ej. `SYT-001` en San Telmo).
     - *Resultado:* El vehículo se bloquea atómicamente en PostgreSQL, cambia a `RESERVED` y emite evento a SQS.
   - Pulsa el botón emergente **"🚀 Desbloquear e Iniciar Viaje"**:
     - *Resultado:* El vehículo pasa a `IN_USE` y se muestra el banner del viaje en curso con tarifa activa.
   - Pulsa **"🏁 Finalizar Viaje"**:
     - *Resultado:* Simula el dropoff en la Playa de Las Canteras, calcula la distancia y coste (1.25€), devuelve la bici a `AVAILABLE` con nueva posición/batería y emite el evento `TripRecordedEvent`.
3. **Pestaña "📊 Panel Cloud & S3 Data Lake":**
   - Haz clic en la pestaña superior derecha.
   - Observa las **métricas en tiempo real**: recaudación acumulada, número de viajes y flota activa.
   - En la tabla del Data Lake, pulsa **"↻ Sincronizar S3"**:
     - *Resultado:* Verás los eventos JSON inmutables bajo la ruta:
       `raw/trips/year=YYYY/month=MM/day=DD/<eventId>.json`

---

## 6. Pruebas por Consola (API directa)

### En Bash (Linux / macOS):
```bash
# 1. Crear reserva
RES=$(curl -s -X POST http://localhost:8080/api/v1/reservations \
  -H "Content-Type: application/json" \
  -d '{"vehicleId":"a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11","userId":"turista-canario"}')
RES_ID=$(echo $RES | grep -o '"id":"[^"]*' | cut -d'"' -f4)

# 2. Iniciar viaje
RIDE=$(curl -s -X POST http://localhost:8080/api/v1/rides/start \
  -H "Content-Type: application/json" \
  -d "{\"reservationId\":\"$RES_ID\"}")
RIDE_ID=$(echo $RIDE | grep -o '"id":"[^"]*' | cut -d'"' -f4)

# 3. Finalizar viaje
curl -s -X POST "http://localhost:8080/api/v1/rides/$RIDE_ID/finish" \
  -H "Content-Type: application/json" \
  -d '{"endLatitude":28.1350,"endLongitude":-15.4380}'
```

### En PowerShell (Windows):
```powershell
# 1. Crear reserva
$res = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/reservations" -Method Post -Body '{"vehicleId":"a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11","userId":"turista-canario"}' -ContentType "application/json"

# 2. Iniciar viaje
$ride = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/rides/start" -Method Post -Body (@{reservationId=$res.id} | ConvertTo-Json) -ContentType "application/json"

# 3. Finalizar viaje
$finish = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/rides/$($ride.id)/finish" -Method Post -Body '{"endLatitude":28.1350,"endLongitude":-15.4380}' -ContentType "application/json"
$finish | ConvertTo-Json
```

---

## 7. Verificación de Recursos Cloud en LocalStack

Para inspeccionar los recursos creados por Terraform dentro de LocalStack:

```bash
# Listar objetos ingeridos en el Data Lake S3
docker compose exec urbanmove-localstack awslocal s3 ls s3://urbanmove-data-lake/raw/ --recursive

# Ver el contenido de un evento específico en S3
docker compose exec urbanmove-localstack awslocal s3 cp s3://urbanmove-data-lake/raw/trips/.../evento.json -

# Inspeccionar la función Lambda de ingesta
docker compose exec urbanmove-localstack awslocal lambda list-functions

# Comprobar la cola de mensajes y Dead Letter Queue (DLQ)
docker compose exec urbanmove-localstack awslocal sqs list-queues
```

---

## 8. Ejecución Automatizada de Tests Unitarios y Web

Para ejecutar la suite de pruebas reproducible sin necesidad de tener Maven ni JDK 21 configurados en tu máquina:

```bash
docker run --rm -v "${PWD}:/app" -w /app maven:3.9.9-eclipse-temurin-21 mvn -pl services/core-fleet test
```

Verificación esperada:
- **`VehicleDomainTest`**: Transiciones de estado y bloqueo de batería baja (<15%).
- **`FleetApplicationServiceTest`**: Orquestación y verificación de emisión de eventos a SQS.
- **`FleetControllerTest`**: Contratos HTTP REST (200 OK, 201 Created).
- **Resultado:** `7 tests run, 0 failures, 0 errors`.

---

## 9. Resolución de Problemas Frecuentes

| Problema | Causa | Solución |
|---|---|---|
| `FATAL: password authentication failed` | Se cambió el password en `.env` pero el volumen de Postgres ya estaba creado con el anterior. | Ejecuta `docker compose down -v` y vuelve a levantar con `docker compose up -d`. |
| `BucketAlreadyOwnedByYou` en Terraform | El bucket fue creado previamente fuera de Terraform. | Reinicia LocalStack con `docker compose restart localstack` y ejecuta de nuevo `terraform apply`. |
| No aparecen objetos en S3 | La ingesta SQS ➔ Lambda es asíncrona. | Espera 2-3 segundos tras finalizar el viaje y pulsa "Sincronizar S3" en el panel. |
| El mapa no carga los iconos o fondo | Sin conexión a Internet para descargar azulejos de OpenStreetMap. | Asegúrate de tener conexión en el navegador (Leaflet usa CDN de OpenStreetMap). |
