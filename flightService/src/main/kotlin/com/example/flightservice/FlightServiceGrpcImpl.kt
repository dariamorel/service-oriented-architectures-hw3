package com.example.flightservice

import com.example.flight.v1.FlightServiceGrpcKt
import com.example.flight.v1.GetFlightRequest
import com.example.flight.v1.ReleaseReservationRequest
import com.example.flight.v1.ReserveSeatsRequest
import com.example.flight.v1.SearchFlightsRequest
import com.example.flight.v1.SearchFlightsResponse
import com.example.flight.v1.SeatReservation
import com.example.flight.v1.GetFlightResponse
import com.example.flight.v1.ReserveSeatsResponse
import com.example.flight.v1.ReleaseReservationResponse
import org.springframework.stereotype.Service

@Service
class FlightServiceGrpcImpl(
    private val flightData: FlightDataService,
) : FlightServiceGrpcKt.FlightServiceCoroutineImplBase() {

    override suspend fun searchFlights(request: SearchFlightsRequest): SearchFlightsResponse {
        val flights = flightData.searchFlights(request)
        return SearchFlightsResponse.newBuilder()
            .addAllFlights(flights)
            .build()
    }

    override suspend fun getFlight(request: GetFlightRequest): GetFlightResponse {
        val flight = flightData.getFlight(request)
        return GetFlightResponse.newBuilder()
            .setFlight(flight)
            .build()
    }

    override suspend fun reserveSeats(request: ReserveSeatsRequest): ReserveSeatsResponse {
        val reservation: SeatReservation = flightData.reserveSeats(request)
        return ReserveSeatsResponse.newBuilder()
            .setReservation(reservation)
            .build()
    }

    override suspend fun releaseReservation(request: ReleaseReservationRequest): ReleaseReservationResponse {
        val reservation: SeatReservation = flightData.releaseReservation(request)
        return ReleaseReservationResponse.newBuilder()
            .setReservation(reservation)
            .build()
    }
}

