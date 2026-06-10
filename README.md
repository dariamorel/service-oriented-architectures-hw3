# service-oriented-architectures-hw3

**Запустить unit тесты:**
`./gradlew :flightService:test :bookingService:test`

**Запустить интеграционные тесты:**
`./gradlew :bookingService:integrationTest` 

**Запустить E2E тесты:**
`./gradlew e2eTest` 

**Посмотреть метрики Prometheus:**
booking-service: http://localhost:8080/metrics
flight-service: http://localhost:8081/metrics
prometheus: http://localhost:9090/

**Открыть Grafana:**
http://localhost:3000/

**Проверить что API отвечает:**
`curl "http://localhost:8080/flights?origin=SVO&destination=LED"`

Нагрузочный тест запускается с docker compose up.

**Как посмотреть alerts:**
http://localhost:9090/alerts
http://localhost:9093/