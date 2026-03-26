package com.example.bookingservice

import com.example.flight.v1.FlightServiceGrpc
import com.example.flight.v1.GetFlightRequest
import com.example.flight.v1.GetFlightResponse
import com.example.flight.v1.ReleaseReservationRequest
import com.example.flight.v1.ReleaseReservationResponse
import com.example.flight.v1.ReserveSeatsRequest
import com.example.flight.v1.ReserveSeatsResponse
import com.example.flight.v1.SearchFlightsRequest
import com.example.flight.v1.SearchFlightsResponse
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class FlightGrpcClient(
    @Value("\${grpc.flight.host}") private val host: String,
    @Value("\${grpc.flight.port}") private val port: Int,
) {
    private val channel: ManagedChannel =
        ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()

    private val stub = FlightServiceGrpc.newBlockingStub(channel)

    fun getFlight(request: GetFlightRequest): GetFlightResponse? = stub.getFlight(request)

    fun searchFlights(request: SearchFlightsRequest): SearchFlightsResponse? = stub.searchFlights(request)

    fun reserveSeats(request: ReserveSeatsRequest): ReserveSeatsResponse? = stub.reserveSeats(request)

    fun releaseReservation(request: ReleaseReservationRequest): ReleaseReservationResponse? = stub.releaseReservation(request)

    @PreDestroy
    fun shutdown() {
        channel.shutdown()
    }
}
