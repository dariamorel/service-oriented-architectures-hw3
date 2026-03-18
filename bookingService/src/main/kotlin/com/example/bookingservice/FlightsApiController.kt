package com.example.bookingservice

import com.example.bookingservice.api.FlightsApi
import com.example.bookingservice.dto.FlightResponse
import com.example.flight.v1.GetFlightRequest
import com.example.flight.v1.SearchFlightsRequest
import com.google.protobuf.Timestamp
import io.grpc.StatusRuntimeException
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RestController
class FlightsApiController(
    private val flights: FlightGrpcClient,
) : FlightsApi {

    override fun searchFlights(origin: String, destination: String, date: LocalDate?): ResponseEntity<List<FlightResponse>> {
        val req = SearchFlightsRequest.newBuilder()
            .setDepartureAirportIata(origin)
            .setArrivalAirportIata(destination)
            .apply {
                if (date != null) {
                    val instant = date.atStartOfDay().toInstant(ZoneOffset.UTC)
                    departureDate = Timestamp.newBuilder()
                        .setSeconds(instant.epochSecond)
                        .setNanos(instant.nano)
                        .build()
                }
            }
            .build()

        val resp = try {
            flights.searchFlights(req)
        } catch (e: StatusRuntimeException) {
            throw ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, e.message, e)
        } ?: throw ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Flight service unavailable")

        val list = resp.flightsList.map { f ->
            FlightResponse().apply {
                val depInstant = Instant.ofEpochSecond(f.departureDate.seconds, f.departureDate.nanos.toLong())
                id = UUID.fromString(f.flightId)
                flightNumber = f.flightNumber
                departureDate = depInstant.atOffset(ZoneOffset.UTC)
                airline = f.airline
                this.origin = f.departureAirportIata
                this.destination = f.arrivalAirportIata
                departureTime = Instant.ofEpochSecond(f.departureTime.seconds, f.departureTime.nanos.toLong()).atOffset(ZoneOffset.UTC)
                arrivalTime = Instant.ofEpochSecond(f.arrivalTime.seconds, f.arrivalTime.nanos.toLong()).atOffset(ZoneOffset.UTC)
                totalSeats = f.totalSeats
                availableSeats = f.availableSeats
                ticketPrice = f.ticketPrice
                status = FlightResponse.StatusEnum.fromValue(f.status.name)
            }
        }

        return ResponseEntity.ok(list)
    }

    override fun getFlight(id: UUID): ResponseEntity<FlightResponse> {
        val flight = try {
            flights.getFlight(
                GetFlightRequest.newBuilder()
                    .setFlightId(id.toString())
                    .build(),
            )?.flight
        } catch (e: StatusRuntimeException) {
            val code = e.status.code
            val http = if (code == io.grpc.Status.Code.NOT_FOUND) org.springframework.http.HttpStatus.NOT_FOUND else org.springframework.http.HttpStatus.BAD_GATEWAY
            throw ResponseStatusException(http, e.message, e)
        } ?: throw ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Flight service unavailable")

        val departure = Instant.ofEpochSecond(flight.departureDate.seconds, flight.departureDate.nanos.toLong())
        val resp = FlightResponse().apply {
            this.id = UUID.fromString(flight.flightId)
            flightNumber = flight.flightNumber
            departureDate = departure.atOffset(ZoneOffset.UTC)
            airline = flight.airline
            origin = flight.departureAirportIata
            destination = flight.arrivalAirportIata
            departureTime = Instant.ofEpochSecond(flight.departureTime.seconds, flight.departureTime.nanos.toLong()).atOffset(ZoneOffset.UTC)
            arrivalTime = Instant.ofEpochSecond(flight.arrivalTime.seconds, flight.arrivalTime.nanos.toLong()).atOffset(ZoneOffset.UTC)
            totalSeats = flight.totalSeats
            availableSeats = flight.availableSeats
            ticketPrice = flight.ticketPrice
            status = FlightResponse.StatusEnum.fromValue(flight.status.name)
        }

        return ResponseEntity.ok(resp)
    }
}

