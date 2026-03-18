FROM flyway/flyway:10-alpine AS flyway

FROM eclipse-temurin:24-jdk AS builder

WORKDIR /workspace

COPY . .

RUN chmod +x ./gradlew && ./gradlew :bookingService:generateProto :bookingService:installDist --no-daemon

FROM eclipse-temurin:24-jre

WORKDIR /app

COPY --from=flyway /flyway /opt/flyway
COPY --from=builder /workspace/bookingService/build/install/bookingService/ /app/
COPY --from=builder /workspace/bookingService/src/main/resources/db/migration /app/db/migration

ENTRYPOINT ["sh", "-c", "/opt/flyway/flyway -url=jdbc:postgresql://${BOOKING_DB_HOST}:${BOOKING_DB_PORT}/${BOOKING_DB_NAME} -user=${BOOKING_DB_USER} -password=${BOOKING_DB_PASSWORD} -locations=filesystem:/app/db/migration -connectRetries=60 migrate && exec /app/bin/bookingService"]
