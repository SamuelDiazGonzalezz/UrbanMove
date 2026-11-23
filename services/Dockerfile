# syntax=docker/dockerfile:1.7
# Imagen común para los 13 microservicios. La etapa de build es idéntica para todos, así que
# BuildKit la ejecuta una sola vez y cada imagen solo copia su JAR (ARG SERVICE).
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY libs libs
COPY services services
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -pl '!services/core-fleet' package

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/services/${SERVICE}/target/app.jar app.jar
USER app
# JVM limitada: 13 servicios + Keycloak + LocalStack deben caber en un portátil de 16 GB.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
