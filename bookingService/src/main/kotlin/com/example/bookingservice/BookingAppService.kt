package com.example.bookingservice

import com.example.bookingservice.dto.BookingResponse
import com.example.bookingservice.dto.CreateBookingRequest
import com.example.flight.v1.GetFlightRequest
import com.example.flight.v1.ReleaseReservationRequest
import com.example.flight.v1.ReserveSeatsRequest
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
class BookingAppService(
    private val flights: FlightGrpcClient,
    private val bookings: BookingRepository,
) {

    @Transactional
    fun create(req: CreateBookingRequest): BookingResponse {
        val flight = try {
            flights.getFlight(
                GetFlightRequest.newBuilder()
                    .setFlightId(req.flightId.toString())
                    .build(),
            )?.flight
        } catch (e: StatusRuntimeException) {
            throw mapGrpc(e, HttpStatus.NOT_FOUND)
        }
        val f = flight ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Flight service returned empty flight")
        val flightNumber = f.flightNumber
        val departure = Instant.ofEpochSecond(f.departureDate.seconds, f.departureDate.nanos.toLong())
        val flightId = req.flightId.toString()

        val bookingId = UUID.randomUUID()
        val reserve = try {
            flights.reserveSeats(
                ReserveSeatsRequest.newBuilder()
                    .setFlightId(flightId)
                    .setBookingId(bookingId.toString())
                    .setSeatCount(req.seatCount)
                    .build(),
            )
        } catch (e: StatusRuntimeException) {
            throw mapGrpc(e, HttpStatus.CONFLICT)
        }

        val reservation = reserve?.reservation
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Flight service returned empty reservation")
        val reservationId = UUID.fromString(reservation.reservationId)
        val totalPrice = f.ticketPrice * req.seatCount.toLong()

        try {
            bookings.save(
                BookingEntity(
                    id = bookingId,
                    userId = req.userId,
                    flightId = req.flightId,
                    passengerName = req.passengerName,
                    passengerEmail = req.passengerEmail,
                    seatCount = req.seatCount,
                    totalPrice = totalPrice,
                    reservationId = reservationId,
                    status = "CONFIRMED",
                ),
            )
        } catch (e: RuntimeException) {
            try {
                flights.releaseReservation(
                    ReleaseReservationRequest.newBuilder()
                        .setBookingId(bookingId.toString())
                        .setReservationId(reservationId.toString())
                        .setFlightId(flightId)
                        .build(),
                )
            } catch (_: StatusRuntimeException) {
            }
            throw e
        }

        return toResponse(
            id = bookingId,
            userId = req.userId,
            flightId = req.flightId,
            flightNumber = flightNumber,
            departure = departure,
            passengerName = req.passengerName,
            email = req.passengerEmail,
            seats = req.seatCount,
            price = totalPrice,
            reservationId = reservationId,
            status = BookingResponse.StatusEnum.CONFIRMED,
        )
    }

    @Transactional
    fun cancel(bookingId: UUID): BookingResponse {
        val entity = bookings.findById(bookingId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        if (entity.status != "CONFIRMED") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Booking is not in CONFIRMED status")
        }

        val reservationId = entity.reservationId
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val fid = entity.flightId ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val flightId = fid.toString()
        try {
            flights.releaseReservation(
                ReleaseReservationRequest.newBuilder()
                    .setBookingId(bookingId.toString())
                    .setReservationId(reservationId.toString())
                    .setFlightId(flightId)
                    .build(),
            )
        } catch (e: StatusRuntimeException) {
            throw mapGrpc(e, HttpStatus.NOT_FOUND)
        }

        entity.status = "CANCELLED"
        entity.cancelledAt = Instant.now()
        bookings.save(entity)

        return toResponse(entity, BookingResponse.StatusEnum.CANCELLED, fetchFlightSnapshot(fid))
    }

    @Transactional(readOnly = true)
    fun get(bookingId: UUID): BookingResponse {
        val entity = bookings.findById(bookingId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val status = if (entity.status == "CANCELLED") BookingResponse.StatusEnum.CANCELLED else BookingResponse.StatusEnum.CONFIRMED
        return toResponse(entity, status, entity.flightId?.let { fetchFlightSnapshot(it) })
    }

    @Transactional(readOnly = true)
    fun listByUser(userId: UUID): List<BookingResponse> =
        bookings.findAllByUserId(userId).map { e ->
            val status = if (e.status == "CANCELLED") BookingResponse.StatusEnum.CANCELLED else BookingResponse.StatusEnum.CONFIRMED
            toResponse(e, status, e.flightId?.let { fetchFlightSnapshot(it) })
        }

    private fun toResponse(
        entity: BookingEntity,
        status: BookingResponse.StatusEnum,
        flightSnapshot: FlightSnapshot? = null,
    ): BookingResponse =
        toResponse(
            entity.id,
            entity.userId,
            entity.flightId,
            flightSnapshot?.flightNumber,
            flightSnapshot?.departure,
            entity.passengerName,
            entity.passengerEmail,
            entity.seatCount,
            entity.totalPrice,
            entity.reservationId,
            status,
        )

    private fun toResponse(
        id: UUID,
        userId: UUID,
        flightId: UUID?,
        flightNumber: String?,
        departure: Instant?,
        passengerName: String,
        email: String,
        seats: Int,
        price: Long,
        reservationId: UUID?,
        status: BookingResponse.StatusEnum,
    ): BookingResponse {
        val r = BookingResponse()
        r.id = id
        r.userId = userId
        r.flightId = flightId
        flightNumber?.let { r.flightNumber = it }
        departure?.let { r.flightDepartureInstant = it.atOffset(ZoneOffset.UTC) }
        r.passengerName = passengerName
        r.passengerEmail = email
        r.seatCount = seats
        r.totalPrice = price
        r.reservationId = reservationId
        r.status = status
        return r
    }

    private fun fetchFlightSnapshot(flightId: UUID): FlightSnapshot? =
        try {
            flights.getFlight(
                GetFlightRequest.newBuilder()
                    .setFlightId(flightId.toString())
                    .build(),
            )?.flight?.let { flight ->
                FlightSnapshot(
                    flight.flightNumber,
                    Instant.ofEpochSecond(flight.departureDate.seconds, flight.departureDate.nanos.toLong()),
                )
            }
        } catch (_: StatusRuntimeException) {
            null
        }

    private data class FlightSnapshot(
        val flightNumber: String,
        val departure: Instant,
    )

    private fun mapGrpc(e: StatusRuntimeException, defaultStatus: HttpStatus): ResponseStatusException {
        val code = e.status.code
        val status = when (code) {
            Status.Code.NOT_FOUND -> HttpStatus.NOT_FOUND
            Status.Code.RESOURCE_EXHAUSTED -> HttpStatus.CONFLICT
            Status.Code.ALREADY_EXISTS -> HttpStatus.CONFLICT
            Status.Code.INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST
            Status.Code.INTERNAL -> HttpStatus.BAD_GATEWAY
            Status.Code.UNAVAILABLE -> HttpStatus.BAD_GATEWAY
            else -> defaultStatus
        }
        return ResponseStatusException(status, e.status.description ?: e.message, e)
    }
}
