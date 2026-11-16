FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar POMs y descargar dependencias primero (cache layer)
COPY pom.xml .
COPY services/core-fleet/pom.xml services/core-fleet/
RUN mvn -B -pl services/core-fleet dependency:go-offline

# Copiar código fuente y compilar
COPY services/core-fleet/src services/core-fleet/src
RUN mvn -B -pl services/core-fleet clean package -DskipTests

# Runtime stage ligero
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/core-fleet/target/core-fleet-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
