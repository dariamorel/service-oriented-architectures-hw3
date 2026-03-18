FROM flyway/flyway:10-alpine AS flyway

FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

ENV GRADLE_USER_HOME=/workspace/.gradle

ENV GRADLE_OPTS="-Dorg.gradle.internal.http.connectionTimeout=600000 -Dorg.gradle.internal.http.socketTimeout=600000"

COPY . .

RUN chmod +x ./gradlew && ./gradlew :bookingService:openApiGenerate :bookingService:generateProto :bookingService:bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=flyway /flyway /opt/flyway
COPY --from=builder /workspace/bookingService/build/libs/*.jar /app/app.jar
COPY --from=builder /workspace/bookingService/src/main/resources/db/migration /app/db/migration

ENTRYPOINT ["sh", "-c", "/opt/flyway/flyway -url=jdbc:postgresql://${BOOKING_DB_HOST}:${BOOKING_DB_PORT}/${BOOKING_DB_NAME} -user=${BOOKING_DB_USER} -password=${BOOKING_DB_PASSWORD} -locations=filesystem:/app/db/migration -connectRetries=60 migrate && exec java -jar /app/app.jar"]
