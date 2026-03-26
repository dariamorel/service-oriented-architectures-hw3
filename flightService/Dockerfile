FROM flyway/flyway:10-alpine AS flyway

FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

ENV GRADLE_USER_HOME=/workspace/.gradle

ENV GRADLE_OPTS="-Dorg.gradle.internal.http.connectionTimeout=600000 -Dorg.gradle.internal.http.socketTimeout=600000"

COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew :flightService:generateProto
RUN ./gradlew :flightService:installDist --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=flyway /flyway /opt/flyway
COPY --from=builder /workspace/flightService/build/install/flightService/ /app/
COPY --from=builder /workspace/flightService/src/main/resources/db/migration /app/db/migration

ENTRYPOINT ["sh", "-c", "/opt/flyway/flyway -url=jdbc:postgresql://${FLIGHT_DB_HOST}:${FLIGHT_DB_PORT}/${FLIGHT_DB_NAME} -user=${FLIGHT_DB_USER} -password=${FLIGHT_DB_PASSWORD} -locations=filesystem:/app/db/migration -connectRetries=60 migrate && exec /app/bin/flightService"]
