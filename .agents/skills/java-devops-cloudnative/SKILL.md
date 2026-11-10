---
name: java-devops-cloudnative
description: Mejores prácticas de DevOps para Java. Dockerfile multi-stage con capas Spring Boot, imágenes JRE optimizadas, ajuste de memoria JVM para cgroups/contenedores, Kubernetes manifests, probes y CI/CD con GitHub Actions.
---

# Java DevOps & Cloud-Native Containerization

Esta skill proporciona las plantillas y reglas estándar para empaquetar, asegurar y desplegar microservicios en Java mediante contenedores y pipelines de CI/CD.

## 1. Multi-Stage Dockerfile de Producción (Capas Spring Boot + Eclipse Temurin)

Las imágenes deben ser:
- **Ligeras**: Sin JDK completo en runtime (solo JRE).
- **Seguras**: Ejecutar como usuario no privilegiado (`appuser`), sin privilegios root.
- **Optimizadas para caché**: Desempaquetando el layered JAR de Spring Boot para que los cambios de código solo reconstruyan la última capa ligera (evitando transferir 80MB de dependencias en cada build).

```dockerfile
# Etapa 1: Extracción de capas del fat JAR
FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /builder
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted/

# Etapa 2: Imagen final mínima para producción
FROM eclipse-temurin:21-jre-alpine
WORKDIR /application

# Crear usuario no root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

# Copiar capas ordenadas por frecuencia de cambio (mayor caché Docker)
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# Opciones JVM para contenedores (reconocimiento de cgroups y límites de memoria)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

---

## 2. Configuración de Spring Boot para Cloud-Native y Kubernetes

En `application.yml`:

```yaml
server:
  port: 8080
  shutdown: graceful  # Espera a terminar peticiones activas antes de apagar

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
        enabled: true  # Activa /actuator/health/liveness y /actuator/health/readiness
      show-details: when_authorized
```

---

## 3. Kubernetes Deployment & Service Manifest con Probes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: urban-mobility-service
  labels:
    app: urban-mobility-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: urban-mobility-service
  template:
    metadata:
      labels:
        app: urban-mobility-service
    spec:
      containers:
        - name: app
          image: ghcr.io/myorg/urban-mobility-service:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1024Mi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 2
          securityContext:
            readOnlyRootFilesystem: false
            allowPrivilegeEscalation: false
            runAsNonRoot: true
            runAsUser: 10001
---
apiVersion: v1
kind: Service
metadata:
  name: urban-mobility-service
spec:
  type: ClusterIP
  ports:
    - port: 80
      targetPort: 8080
      protocol: TCP
  selector:
    app: urban-mobility-service
```

---

## 4. Pipeline CI/CD con GitHub Actions (.github/workflows/ci.yml)

```yaml
name: CI & Docker Build

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven' # o 'gradle'

      - name: Build and Run Tests
        run: ./mvnw clean verify

      - name: Vulnerability Scan with Trivy (FS)
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          ignore-unfixed: true
          format: 'table'
          severity: 'CRITICAL,HIGH'
```
