# Contenedores y empaquetado — UrbanMove

> **Ámbito:** los 13 microservicios.
> **Objetivo:** imágenes pequeñas, reproducibles y sin privilegios, con arranque y parada
> limpios.
> **Relacionados:** [LOCAL-DEV.md](LOCAL-DEV.md) (entorno), [CI-CD.md](../03-operacion/CI-CD.md)
> (pipeline), [ARCHITECTURE.md](../01-arquitectura/ARCHITECTURE.md) (despliegue).

El despliegue objetivo es **Docker Compose en local** y **EC2 con Compose o ECS Fargate**
en AWS. EKS y Kubernetes quedan fuera del alcance por coste y complejidad; aun así, los
endpoints de *probe* son compatibles con Kubernetes por si se migra.

---

## 1. Reglas de imagen

| Regla | Motivo |
|---|---|
| Multi-stage | El JDK y Maven no viajan a producción |
| Base JRE, no JDK | Menos tamaño y menos superficie de ataque |
| `eclipse-temurin:21-jre-alpine` | Coherente con el JDK local |
| Usuario no root (`appuser`) | Si el proceso se compromete, no es root |
| Capas de Spring Boot | Cambiar código no invalida la capa de dependencias |
| Versión fija, nunca `latest` | Reproducibilidad y demo estable |
| Sin secretos en la imagen | Configuración por variables de entorno |
| Un proceso por contenedor | Escalado y observabilidad simples |

---

## 2. Dockerfile de referencia

```dockerfile
# ---------- Etapa 1: extraer las capas del fat JAR ----------
FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /builder

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar

# Spring Boot 3.4+ (modo tools). En versiones anteriores: -Djarmode=layertools
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted/

# ---------- Etapa 2: imagen final ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /application

# Usuario sin privilegios
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

# Orden por frecuencia de cambio: lo más estable primero
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

Notas:

- `exec` hace que la JVM sea el PID 1 y reciba `SIGTERM` para la parada limpia.
- `--layers` separa dependencias, *loader*, *snapshots* y aplicación: un cambio de código
  solo reconstruye la última capa.
- No se copia `target/` completo ni el `.jar` original: solo las capas extraídas.

### 2.1. `.dockerignore`

```text
target/
.git/
.idea/
.vscode/
*.iml
*.log
.env
.env.*
!.env.example
docs/
reports/
```

Sin `.dockerignore`, el contexto de build incluye `target/`, `.git/` y los informes: builds
lentos y capas enormes.

### 2.2. Etiquetado

| Entorno | Etiqueta | Ejemplo |
|---|---|---|
| Local | `dev` | `urbanmove/inventory:dev` |
| CI | SHA corto | `ghcr.io/<org>/inventory:3f9c1a2` |
| Demo | versión + SHA | `ghcr.io/<org>/inventory:1.0.0-3f9c1a2` |

Nunca se despliega `latest`: no se sabe qué contiene y no se puede revertir con precisión.

---

## 3. Memoria y JVM en contenedores

| Ajuste | Valor | Por qué |
|---|---|---|
| `-XX:MaxRAMPercentage=75.0` | 75 % del límite del contenedor | La JVM respeta cgroups y no se pasa |
| `-XX:+ExitOnOutOfMemoryError` | activado | Un OOM deja el contenedor en fallo y el orquestador lo reinicia |
| `-XX:+UseG1GC` | G1 | Buen equilibrio latencia/throughput en servicios pequeños |
| `-Xmx` fijo | evitar | Rompe la portabilidad entre local y AWS |
| Heap del servicio local | ~256 MB por servicio | Permite levantar varios sin ahogar el portátil |

Si el contenedor tiene límite de 512 MB, la JVM usará ~384 MB de heap y dejará el resto
para metaspace, hilos, código y memoria directa. Por eso el límite del contenedor debe ser
mayor que el heap esperado, no igual.

---

## 4. Configuración de Spring Boot

```yaml
server:
  port: 8080
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

| Endpoint | Uso |
|---|---|
| `/actuator/health/liveness` | ¿El proceso está vivo? Si falla, se reinicia |
| `/actuator/health/readiness` | ¿Puede recibir tráfico? Si falla, se saca del balanceador |
| `/actuator/prometheus` | Métricas para Prometheus |
| `/actuator/info` | Versión y build |

`server.shutdown=graceful` espera a terminar las peticiones en curso antes de apagar. Sin
esto, cada despliegue corta reservas a medias.

---

## 5. `docker-compose.yml`: contrato del entorno

El Compose es la única definición del entorno local. Se versiona y se ejecuta igual en
Windows, macOS y Linux.

### 5.1. Plantilla de servicio

```yaml
services:
  reservations:
    build:
      context: ./services/reservations
    image: urbanmove/reservations:dev
    profiles: ["core", "all"]
    ports:
      - "8106:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/reservations_db
      SPRING_DATASOURCE_USERNAME: ${RESERVATIONS_DB_USER}
      SPRING_DATASOURCE_PASSWORD: ${RESERVATIONS_DB_PASSWORD}
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://keycloak:8180/realms/urbanmove
      AWS_ENDPOINT_URL: http://localstack:4566
    depends_on:
      postgres:
        condition: service_healthy
      localstack-init:
        condition: service_completed_successfully
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health/readiness"]
      interval: 10s
      timeout: 3s
      retries: 5
      start_period: 40s
    deploy:
      resources:
        limits:
          memory: 512M
    restart: unless-stopped
```

Reglas:

- `profiles` evita levantar los 13 servicios cada día (`infra`, `core`, `analytics`,
  `all`).
- `depends_on` con `condition` y **healthchecks**, nunca `sleep`.
- `start_period` generoso: Spring tarda en arrancar y no debe contar como fallo.
- Variables desde `.env`, nunca credenciales escritas en el YAML.
- Límite de memoria por servicio para que un bucle infinito no tumbe el portátil.

### 5.2. Healthchecks de la infraestructura

| Servicio | Comando |
|---|---|
| PostgreSQL | `pg_isready -U urbanmove` |
| Keycloak | `curl -f http://localhost:9000/health/ready` |
| LocalStack | `curl -f http://localhost:4566/_localstack/health` |
| Prometheus | `wget -qO- http://localhost:9090/-/healthy` |
| Grafana | `curl -f http://localhost:3000/api/health` |
| Spring Boot | `wget -qO- http://localhost:8080/actuator/health/readiness` |

El orden completo de arranque está en [LOCAL-DEV.md](LOCAL-DEV.md), sección 6.2.

### 5.3. Apagado

```bash
docker compose down            # para sin borrar volúmenes
docker compose down -v         # entorno limpio (borra datos)
```

Con `stop_grace_period: 40s` y `server.shutdown=graceful`, el contenedor recibe `SIGTERM`,
termina las peticiones en curso y cierra el pool de conexiones antes de morir.

---

## 6. Seguridad del contenedor

| Control | Implementación |
|---|---|
| Usuario no root | `USER appuser:appgroup` |
| Sin escalada de privilegios | `security_opt: no-new-privileges:true` |
| Sistema de ficheros de solo lectura | `read_only: true` + `tmpfs: /tmp` (donde aplique) |
| Capacidades mínimas | `cap_drop: [ALL]` |
| Sin secretos en la imagen | Variables de entorno o Secrets Manager |
| Escaneo de imagen | Trivy en el pipeline |

```yaml
    read_only: true
    tmpfs:
      - /tmp
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
```

Spring Boot necesita escribir en `/tmp` (Tomcat, extracción de recursos); por eso se monta
un `tmpfs` en lugar de dejar el sistema de ficheros escribible.

---

## 7. Alternativas evaluadas

| Alternativa | Ventaja | Inconveniente | Decisión |
|---|---|---|---|
| Dockerfile multi-stage | Control total, reproducible | Hay que mantenerlo | **Elegida** |
| Buildpacks (`spring-boot:build-image`) | Cero Dockerfile, parches de base | Menos control de usuario/capas | Válida como respaldo |
| Jib | Sin daemon, buenas capas | Configuración extra | No necesaria |
| `latest` en Compose | Cómodo al principio | Demo impredecible | **Prohibido** |

---

## 8. Despliegue en AWS (contexto)

| Elemento | Decisión |
|---|---|
| Cómputo por defecto | Una EC2 con Docker Compose |
| Cómputo alternativo | ECS Fargate + ALB si hay crédito |
| Kubernetes / EKS | Fuera de alcance |
| Registro de imágenes | GHCR en CI; ECR si se despliega en ECS |
| Configuración | Variables de entorno + Secrets Manager / SSM |
| Logs | CloudWatch Logs con `correlationId` |
| *Probes* | Los mismos endpoints de Actuator |

Las tareas de ECS usan la misma imagen y los mismos endpoints que Compose: no hay dos
formas de empaquetar. El detalle de la topología está en [ARCHITECTURE.md](../01-arquitectura/ARCHITECTURE.md),
sección 10.

---

## 9. Checklist de contenedor por servicio

- [ ] Dockerfile multi-stage con JRE Alpine y usuario no root.
- [ ] Capas de Spring Boot extraídas y ordenadas.
- [ ] `.dockerignore` excluye `target/`, `.git/`, `.env` y docs.
- [ ] `JAVA_OPTS` con `MaxRAMPercentage` y `ExitOnOutOfMemoryError`.
- [ ] `server.shutdown=graceful` y `timeout-per-shutdown-phase`.
- [ ] Endpoints de *liveness* y *readiness* habilitados.
- [ ] Healthcheck en Compose con `start_period` suficiente.
- [ ] Límite de memoria definido.
- [ ] Sin secretos en imagen ni en YAML versionado.
- [ ] Imagen etiquetada por SHA; nunca `latest`.
- [ ] Trivy sin vulnerabilidades críticas.
