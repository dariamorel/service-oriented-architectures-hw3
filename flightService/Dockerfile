FROM flyway/flyway:10-alpine AS flyway

FROM eclipse-temurin:24-jdk AS builder

WORKDIR /workspace

COPY . .

RUN chmod +x ./gradlew && ./gradlew :flightService:generateProto :flightService:installDist --no-daemon

FROM eclipse-temurin:24-jre

WORKDIR /app

COPY --from=flyway /flyway /opt/flyway
COPY --from=builder /workspace/flightService/build/install/flightService/ /app/
COPY --from=builder /workspace/flightService/src/main/resources/db/migration /app/db/migration

ENTRYPOINT ["sh", "-c", "/opt/flyway/flyway -url=jdbc:postgresql://${FLIGHT_DB_HOST}:${FLIGHT_DB_PORT}/${FLIGHT_DB_NAME} -user=${FLIGHT_DB_USER} -password=${FLIGHT_DB_PASSWORD} -locations=filesystem:/app/db/migration -connectRetries=60 migrate && exec /app/bin/flightService"]
