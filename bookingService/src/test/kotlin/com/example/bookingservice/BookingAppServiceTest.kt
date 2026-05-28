package com.example.bookingservice

import com.example.bookingservice.dto.BookingResponse
import com.example.bookingservice.dto.CreateBookingRequest
import com.example.flight.v1.Flight
import com.example.flight.v1.GetFlightRequest
import com.example.flight.v1.GetFlightResponse
import com.example.flight.v1.ReleaseReservationRequest
import com.example.flight.v1.ReleaseReservationResponse
import com.example.flight.v1.ReserveSeatsRequest
import com.example.flight.v1.ReserveSeatsResponse
import com.example.flight.v1.SeatReservation
import com.example.flight.v1.SeatReservationStatus
import com.google.protobuf.Timestamp
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BookingAppServiceTest {

    private val flights = mockk<FlightGrpcClient>()
    private val bookings = mockk<BookingRepository>()
    private val service = BookingAppService(flights, bookings)

    @Test
    fun `create saves booking and returns confirmed response`() {
        val userId = UUID.randomUUID()
        val flightId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val reservationId = UUID.randomUUID()
        val request = CreateBookingRequest().apply {
            this.userId = userId
            this.flightId = flightId
            passengerName = "Ivan Ivanov"
            passengerEmail = "ivan@example.com"
            seatCount = 2
        }
        val savedBooking = slot<BookingEntity>()

        every { flights.getFlight(any()) } returns GetFlightResponse.newBuilder()
            .setFlight(sampleFlightProto(flightId))
            .build()
        every { flights.reserveSeats(any()) } returns ReserveSeatsResponse.newBuilder()
            .setReservation(
                SeatReservation.newBuilder()
                    .setReservationId(reservationId.toString())
                    .setFlightId(flightId.toString())
                    .setReservedSeats(2)
                    .setStatus(SeatReservationStatus.ACTIVE)
                    .build(),
            )
            .build()
        every { bookings.save(capture(savedBooking)) } answers { savedBooking.captured }

        val response = service.create(request)

        assertEquals(BookingResponse.StatusEnum.CONFIRMED, response.status)
        assertEquals(userId, response.userId)
        assertEquals(flightId, response.flightId)
        assertEquals(2, response.seatCount)
        assertEquals(10_000L, response.totalPrice)
        assertEquals(reservationId, response.reservationId)
        assertEquals("CONFIRMED", savedBooking.captured.status)
        verify(exactly = 1) { flights.reserveSeats(any()) }
    }

    @Test
    fun `get returns booking when it exists`() {
        val bookingId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val flightId = UUID.randomUUID()
        val entity = BookingEntity(
            id = bookingId,
            userId = userId,
            flightId = flightId,
            passengerName = "Ivan Ivanov",
            passengerEmail = "ivan@example.com",
            seatCount = 1,
            totalPrice = 5000,
            reservationId = UUID.randomUUID(),
            status = "CONFIRMED",
        )

        every { bookings.findById(bookingId) } returns Optional.of(entity)
        every { flights.getFlight(any()) } returns GetFlightResponse.newBuilder()
            .setFlight(sampleFlightProto(flightId))
            .build()

        val response = service.get(bookingId)

        assertEquals(bookingId, response.id)
        assertEquals(BookingResponse.StatusEnum.CONFIRMED, response.status)
        assertEquals("SU-100", response.flightNumber)
    }

    @Test
    fun `get throws NOT_FOUND when booking is missing`() {
        val bookingId = UUID.randomUUID()
        every { bookings.findById(bookingId) } returns Optional.empty()

        val error = assertThrows(ResponseStatusException::class.java) {
            service.get(bookingId)
        }

        assertEquals(404, error.statusCode.value())
    }

    @Test
    fun `cancel releases reservation and marks booking as cancelled`() {
        val bookingId = UUID.randomUUID()
        val flightId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        val entity = BookingEntity(
            id = bookingId,
            userId = UUID.randomUUID(),
            flightId = flightId,
            passengerName = "Ivan Ivanov",
            passengerEmail = "ivan@example.com",
            seatCount = 2,
            totalPrice = 10_000,
            reservationId = reservationId,
            status = "CONFIRMED",
        )

        every { bookings.findById(bookingId) } returns Optional.of(entity)
        every { flights.releaseReservation(any()) } returns ReleaseReservationResponse.getDefaultInstance()
        every { bookings.save(any()) } answers { firstArg() }
        every { flights.getFlight(any()) } returns GetFlightResponse.newBuilder()
            .setFlight(sampleFlightProto(flightId))
            .build()

        val response = service.cancel(bookingId)

        assertEquals(BookingResponse.StatusEnum.CANCELLED, response.status)
        assertEquals("CANCELLED", entity.status)
        verify(exactly = 1) {
            flights.releaseReservation(
                ReleaseReservationRequest.newBuilder()
                    .setBookingId(bookingId.toString())
                    .setReservationId(reservationId.toString())
                    .setFlightId(flightId.toString())
                    .build(),
            )
        }
    }

    private fun sampleFlightProto(flightId: UUID): Flight {
        val departure = Instant.parse("2026-04-01T07:00:00Z")
        return Flight.newBuilder()
            .setFlightId(flightId.toString())
            .setFlightNumber("SU-100")
            .setDepartureDate(
                Timestamp.newBuilder()
                    .setSeconds(departure.epochSecond)
                    .setNanos(departure.nano)
                    .build(),
            )
            .setTicketPrice(5000)
            .build()
    }
}
