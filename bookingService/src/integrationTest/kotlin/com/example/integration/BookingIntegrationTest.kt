package com.example.integration

import com.example.bookingservice.BookingServiceApplication
import com.example.bookingservice.dto.BookingResponse
import com.example.bookingservice.dto.CreateBookingRequest
import com.example.flightservice.FlightServiceApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.io.FileSystemResource
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.lifecycle.Startables
import java.net.ServerSocket
import java.nio.file.Path
import java.sql.Connection
import java.util.UUID
import java.util.stream.Stream
import javax.sql.DataSource

@Testcontainers
@SpringBootTest(
    classes = [BookingServiceApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class BookingIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var bookingDataSource: DataSource

    @LocalServerPort
    private var port: Int = 0

    private lateinit var bookingJdbc: JdbcTemplate
    private val flightJdbc: JdbcTemplate
        get() = JdbcTemplate(flightDataSource())

    @BeforeEach
    fun resetDatabase() {
        bookingJdbc = JdbcTemplate(bookingDataSource)

        bookingJdbc.update("delete from bookings")
        flightJdbc.update("delete from seat_reservations")
        flightJdbc.update("update flights set available_seats = 180 where id = ?", AVAILABLE_FLIGHT_ID)
        flightJdbc.update("update flights set available_seats = 0 where id = ?", FULL_FLIGHT_ID)
    }

    @Test
    fun `creates booking through booking service and reserves seats in flight service`() {
        val userId = UUID.randomUUID()
        val request = CreateBookingRequest().apply {
            this.userId = userId
            this.flightId = AVAILABLE_FLIGHT_ID
            passengerName = "Integration Test"
            passengerEmail = "integration@example.com"
            seatCount = 2
        }

        val response = restTemplate.postForEntity(
            "http://localhost:$port/bookings",
            request,
            BookingResponse::class.java,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body).isNotNull
        val booking = response.body!!
        assertThat(booking.status).isEqualTo(BookingResponse.StatusEnum.CONFIRMED)
        assertThat(booking.userId).isEqualTo(userId)
        assertThat(booking.flightId).isEqualTo(AVAILABLE_FLIGHT_ID)
        assertThat(booking.seatCount).isEqualTo(2)
        assertThat(booking.totalPrice).isEqualTo(10_000L)
        assertThat(booking.reservationId).isNotNull

        val savedBookings = bookingJdbc.queryForObject(
            "select count(*) from bookings where id = ? and status = 'CONFIRMED'",
            Int::class.java,
            booking.id,
        )
        val availableSeats = flightJdbc.queryForObject(
            "select available_seats from flights where id = ?",
            Int::class.java,
            AVAILABLE_FLIGHT_ID,
        )
        val activeReservations = flightJdbc.queryForObject(
            "select count(*) from seat_reservations where booking_id = ? and status = 'ACTIVE'",
            Int::class.java,
            booking.id,
        )

        assertThat(savedBookings).isEqualTo(1)
        assertThat(availableSeats).isEqualTo(178)
        assertThat(activeReservations).isEqualTo(1)
    }

    @Test
    fun `does not create booking when flight service reports no available seats`() {
        val userId = UUID.randomUUID()
        val request = CreateBookingRequest().apply {
            this.userId = userId
            this.flightId = FULL_FLIGHT_ID
            passengerName = "No Seats"
            passengerEmail = "no-seats@example.com"
            seatCount = 1
        }

        val response = restTemplate.postForEntity(
            "http://localhost:$port/bookings",
            request,
            String::class.java,
        )

        val savedBookings = bookingJdbc.queryForObject(
            "select count(*) from bookings where user_id = ?",
            Int::class.java,
            userId,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(savedBookings).isZero()
    }

    companion object {
        private val AVAILABLE_FLIGHT_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        private val FULL_FLIGHT_ID: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

        private val grpcPort: Int = findFreePort()
        private var migrationsApplied = false
        private lateinit var flightContext: ConfigurableApplicationContext

        @Container
        private val bookingDb = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("booking")
            .withUsername("postgres")
            .withPassword("postgres")

        @Container
        private val flightDb = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("flight")
            .withUsername("postgres")
            .withPassword("postgres")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            Startables.deepStart(Stream.of(bookingDb, flightDb)).join()
            applyMigrations()

            registry.add("spring.datasource.url", bookingDb::getJdbcUrl)
            registry.add("spring.datasource.username", bookingDb::getUsername)
            registry.add("spring.datasource.password", bookingDb::getPassword)
            registry.add("grpc.flight.host") { "localhost" }
            registry.add("grpc.flight.port") { grpcPort }
        }

        @JvmStatic
        @BeforeAll
        fun startFlightService() {
            Startables.deepStart(Stream.of(bookingDb, flightDb)).join()
            applyMigrations()

            System.setProperty("FLIGHT_DB_HOST", flightDb.host)
            System.setProperty("FLIGHT_DB_PORT", flightDb.getMappedPort(5432).toString())
            System.setProperty("FLIGHT_DB_NAME", flightDb.databaseName)
            System.setProperty("FLIGHT_DB_USER", flightDb.username)
            System.setProperty("FLIGHT_DB_PASSWORD", flightDb.password)
            System.setProperty("GRPC_PORT", grpcPort.toString())

            flightContext = SpringApplicationBuilder(FlightIntegrationApplication::class.java)
                .properties(
                    "spring.main.web-application-type=none",
                )
                .run()
        }

        @JvmStatic
        @AfterAll
        fun stopFlightService() {
            if (::flightContext.isInitialized) {
                flightContext.close()
            }
            listOf(
                "FLIGHT_DB_HOST",
                "FLIGHT_DB_PORT",
                "FLIGHT_DB_NAME",
                "FLIGHT_DB_USER",
                "FLIGHT_DB_PASSWORD",
                "GRPC_PORT",
            ).forEach(System::clearProperty)
        }

        private fun applyMigrations() {
            if (migrationsApplied) {
                return
            }

            bookingDataSource().connection.use { connection ->
                runScript(connection, "bookingService/src/main/resources/db/migration/V1__create_booking_schema.sql")
            }
            flightDataSource().connection.use { connection ->
                runScript(connection, "flightService/src/main/resources/db/migration/V1__create_flight_schema.sql")
                runScript(connection, "flightService/src/main/resources/db/migration/V2__seed_flights.sql")
            }

            migrationsApplied = true
        }

        private fun bookingDataSource(): DataSource =
            DriverManagerDataSource(bookingDb.jdbcUrl, bookingDb.username, bookingDb.password)

        private fun flightDataSource(): DataSource =
            DriverManagerDataSource(flightDb.jdbcUrl, flightDb.username, flightDb.password)

        private fun runScript(connection: Connection, path: String) {
            ScriptUtils.executeSqlScript(connection, FileSystemResource(projectRoot().resolve(path)))
        }

        private fun projectRoot(): Path {
            val currentDir = Path.of(System.getProperty("user.dir"))
            return if (currentDir.fileName.toString() == "bookingService") currentDir.parent else currentDir
        }

        private fun findFreePort(): Int =
            ServerSocket(0).use { it.localPort }
    }

    @SpringBootApplication(scanBasePackages = ["com.example.flightservice"])
    @EntityScan("com.example.flightservice")
    @EnableJpaRepositories("com.example.flightservice")
    private open class FlightIntegrationApplication
}
