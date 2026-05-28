package com.example.flightservice

import com.example.flight.v1.GetFlightRequest
import com.example.flight.v1.ReleaseReservationRequest
import com.example.flight.v1.ReserveSeatsRequest
import com.example.flight.v1.SeatReservationStatus
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class FlightDataServiceTest {

    private val flightRepository = mockk<FlightRepository>()
    private val seatReservationRepository = mockk<SeatReservationRepository>()
    private val service = FlightDataService(flightRepository, seatReservationRepository)

    @Test
    fun `getFlight returns flight proto when flight exists`() {
        val flightId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val entity = sampleFlight(flightId = flightId)

        every { flightRepository.findById(flightId) } returns Optional.of(entity)

        val response = service.getFlight(
            GetFlightRequest.newBuilder().setFlightId(flightId.toString()).build(),
        )

        assertEquals("SU-100", response.flightNumber)
        assertEquals(180, response.availableSeats)
        assertEquals(flightId.toString(), response.flightId)
    }

    @Test
    fun `getFlight throws NOT_FOUND when flight is missing`() {
        val flightId = UUID.randomUUID()
        every { flightRepository.findById(flightId) } returns Optional.empty()

        val error = assertThrows(StatusRuntimeException::class.java) {
            service.getFlight(GetFlightRequest.newBuilder().setFlightId(flightId.toString()).build())
        }

        assertEquals(Status.Code.NOT_FOUND, error.status.code)
    }

    @Test
    fun `reserveSeats decreases available seats and creates reservation`() {
        val flightId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        val flight = sampleFlight(flightId = flightId, availableSeats = 10)
        val savedFlight = slot<FlightEntity>()

        every { flightRepository.findForUpdate(flightId) } returns flight
        every { flightRepository.save(capture(savedFlight)) } answers { savedFlight.captured }
        every { seatReservationRepository.saveAndFlush(any()) } answers {
            firstArg<SeatReservationEntity>().apply { id = UUID.randomUUID() }
        }

        val reservation = service.reserveSeats(
            ReserveSeatsRequest.newBuilder()
                .setFlightId(flightId.toString())
                .setBookingId(bookingId.toString())
                .setSeatCount(3)
                .build(),
        )

        assertEquals(7, savedFlight.captured.availableSeats)
        assertEquals(bookingId.toString(), reservation.bookingId)
        assertEquals(3, reservation.reservedSeats)
        assertEquals(SeatReservationStatus.ACTIVE, reservation.status)
        verify(exactly = 1) { flightRepository.save(any()) }
    }

    @Test
    fun `reserveSeats throws RESOURCE_EXHAUSTED when not enough seats`() {
        val flightId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        every { flightRepository.findForUpdate(flightId) } returns sampleFlight(flightId = flightId, availableSeats = 1)

        val error = assertThrows(StatusRuntimeException::class.java) {
            service.reserveSeats(
                ReserveSeatsRequest.newBuilder()
                    .setFlightId(flightId.toString())
                    .setBookingId(bookingId.toString())
                    .setSeatCount(2)
                    .build(),
            )
        }

        assertEquals(Status.Code.RESOURCE_EXHAUSTED, error.status.code)
    }

    @Test
    fun `releaseReservation returns seats to flight`() {
        val flightId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        val flight = sampleFlight(flightId = flightId, availableSeats = 5)
        val reservation = SeatReservationEntity(
            id = reservationId,
            flight = flight,
            bookingId = bookingId,
            reservedSeats = 3,
            status = "ACTIVE",
        )
        val savedFlight = slot<FlightEntity>()

        every { seatReservationRepository.findForUpdate(bookingId, "ACTIVE") } returns reservation
        every { flightRepository.findForUpdate(flightId) } returns flight
        every { flightRepository.save(capture(savedFlight)) } answers { savedFlight.captured }
        every { seatReservationRepository.save(any()) } answers { firstArg() }

        val released = service.releaseReservation(
            ReleaseReservationRequest.newBuilder()
                .setBookingId(bookingId.toString())
                .setReservationId(reservationId.toString())
                .setFlightId(flightId.toString())
                .build(),
        )

        assertEquals(8, savedFlight.captured.availableSeats)
        assertEquals(SeatReservationStatus.RELEASED, released.status)
    }

    private fun sampleFlight(flightId: UUID, availableSeats: Int = 180): FlightEntity {
        val departure = Instant.parse("2026-04-01T07:00:00Z")
        return FlightEntity(
            id = flightId,
            flightNumber = "SU-100",
            departureDate = Instant.parse("2026-04-01T00:00:00Z"),
            airline = "Aeroflot",
            departureAirportIata = "SVO",
            arrivalAirportIata = "LED",
            departureTime = departure,
            arrivalTime = Instant.parse("2026-04-01T08:30:00Z"),
            totalSeats = 180,
            availableSeats = availableSeats,
            ticketPrice = 5000,
            status = "SCHEDULED",
        )
    }
}
