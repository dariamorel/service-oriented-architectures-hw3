package com.example.flightservice

import com.example.flight.v1.Flight
import com.example.flight.v1.FlightStatus
import com.example.flight.v1.GetFlightRequest
import com.example.flight.v1.ReleaseReservationRequest
import com.example.flight.v1.ReserveSeatsRequest
import com.example.flight.v1.SearchFlightsRequest
import com.example.flight.v1.SeatReservation
import com.example.flight.v1.SeatReservationStatus
import com.google.protobuf.Timestamp
import io.grpc.Status
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class FlightDataService(
    private val flightRepository: FlightRepository,
    private val seatReservationRepository: SeatReservationRepository,
) {

    @Transactional(readOnly = true)
    fun searchFlights(request: SearchFlightsRequest): List<Flight> {
        val list = if (request.hasDepartureDate()) {
            val instant = timestampToInstant(request.departureDate)
            flightRepository.findExistingFlightsWithDepartureDate(
                "SCHEDULED", request.departureAirportIata, request.arrivalAirportIata, instant,
            )
        } else {
            flightRepository.findExistingFlights("SCHEDULED", request.departureAirportIata, request.arrivalAirportIata)
        }
        return list.map { it.toProto() }
    }

    @Transactional(readOnly = true)
    fun getFlight(request: GetFlightRequest): Flight {
        val id = try {
            UUID.fromString(request.flightId)
        } catch (_: IllegalArgumentException) {
            throw Status.INVALID_ARGUMENT.withDescription("flight_id must be a UUID").asRuntimeException()
        }
        val flight = flightRepository.findById(id).orElse(null)
            ?: throw Status.NOT_FOUND.withDescription("Flight not found").asRuntimeException()
        return flight.toProto()
    }

    @Transactional
    fun reserveSeats(request: ReserveSeatsRequest): SeatReservation {
        val seatCount = request.seatCount.toLong()
        if (seatCount <= 0L) {
            throw Status.INVALID_ARGUMENT.withDescription("seat_count must be > 0").asRuntimeException()
        }
        val bookingId = try {
            UUID.fromString(request.bookingId)
        } catch (_: IllegalArgumentException) {
            throw Status.INVALID_ARGUMENT.withDescription("booking_id must be a UUID").asRuntimeException()
        }
        val flightId = try {
            UUID.fromString(request.flightId)
        } catch (_: IllegalArgumentException) {
            throw Status.INVALID_ARGUMENT.withDescription("flight_id must be a UUID").asRuntimeException()
        }
        val flight = flightRepository.findForUpdate(flightId)
            ?: throw Status.NOT_FOUND.withDescription("Flight not found").asRuntimeException()

        if (flight.availableSeats < seatCount) {
            throw Status.RESOURCE_EXHAUSTED.withDescription("Not enough available seats").asRuntimeException()
        }

        flight.availableSeats -= seatCount.toInt()
        flightRepository.save(flight)

        val entity = try {
            seatReservationRepository.saveAndFlush(
                SeatReservationEntity(
                    flight = flight,
                    bookingId = bookingId,
                    reservedSeats = seatCount.toInt(),
                    status = "ACTIVE",
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            throw Status.ALREADY_EXISTS.withDescription("Reservation for booking_id already exists").asRuntimeException()
        }

        return entity.toProto()
    }

    @Transactional
    fun releaseReservation(request: ReleaseReservationRequest): SeatReservation {
        val bookingId = try {
            UUID.fromString(request.bookingId)
        } catch (_: IllegalArgumentException) {
            throw Status.INVALID_ARGUMENT.withDescription("booking_id must be a UUID").asRuntimeException()
        }
        val expectedReservationId = request.reservationId.takeIf { it.isNotBlank() }

        val active = seatReservationRepository.findForUpdate(bookingId, "ACTIVE")
            ?: throw Status.NOT_FOUND.withDescription("Active reservation not found").asRuntimeException()

        if (expectedReservationId != null && expectedReservationId != active.id.toString()) {
            throw Status.INVALID_ARGUMENT.withDescription("reservation_id does not match active reservation").asRuntimeException()
        }

        if (request.flightId.isNotBlank() && request.flightId != active.flight.id.toString()) {
            throw Status.INVALID_ARGUMENT.withDescription("flight_id does not match active reservation").asRuntimeException()
        }

        val flight = flightRepository.findForUpdate(active.flight.id)
            ?: throw Status.NOT_FOUND.withDescription("Flight not found").asRuntimeException()
        flight.availableSeats += active.reservedSeats
        flightRepository.save(flight)

        active.status = "RELEASED"
        active.releasedAt = Instant.now()
        val saved = seatReservationRepository.save(active)

        return saved.toProto()
    }

    private fun timestampToInstant(ts: Timestamp): Instant =
        Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong())

    private fun FlightEntity.toProto(): Flight =
        Flight.newBuilder()
            .setFlightId(id.toString())
            .setFlightNumber(flightNumber)
            .setDepartureDate(
                Timestamp.newBuilder()
                    .setSeconds(departureDate.epochSecond)
                    .setNanos(departureDate.nano)
                    .build(),
            )
            .setAirline(airline)
            .setDepartureAirportIata(departureAirportIata)
            .setArrivalAirportIata(arrivalAirportIata)
            .setDepartureTime(
                Timestamp.newBuilder()
                    .setSeconds(departureTime.epochSecond)
                    .setNanos(departureTime.nano)
                    .build(),
            )
            .setArrivalTime(
                Timestamp.newBuilder()
                    .setSeconds(arrivalTime.epochSecond)
                    .setNanos(arrivalTime.nano)
                    .build(),
            )
            .setTotalSeats(totalSeats)
            .setAvailableSeats(availableSeats)
            .setTicketPrice(ticketPrice)
            .setStatus(FlightStatus.valueOf(status))
            .build()

    private fun SeatReservationEntity.toProto(): SeatReservation {
        val id = id ?: throw Status.INTERNAL.withDescription("Reservation id missing").asRuntimeException()
        val created = createdAt ?: Instant.now()
        val builder = SeatReservation.newBuilder()
            .setReservationId(id.toString())
            .setFlightId(flight.id.toString())
            .setBookingId(bookingId.toString())
            .setReservedSeats(reservedSeats)
            .setStatus(SeatReservationStatus.valueOf(status))
            .setCreatedAt(
                Timestamp.newBuilder()
                    .setSeconds(created.epochSecond)
                    .setNanos(created.nano)
                    .build(),
            )
        releasedAt?.let { ra ->
            builder.setReleasedAt(
                Timestamp.newBuilder()
                    .setSeconds(ra.epochSecond)
                    .setNanos(ra.nano)
                    .build(),
            )
        }
        return builder.build()
    }
}
