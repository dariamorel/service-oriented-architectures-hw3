package com.example.flightservice

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "flights")
class FlightEntity(
    @Id
    @Column(columnDefinition = "uuid")
    val id: UUID,

    @Column(name = "flight_number", nullable = false)
    val flightNumber: String,

    @Column(name = "departure_date", nullable = false)
    val departureDate: Instant,

    @Column(nullable = false)
    val airline: String,

    @Column(name = "departure_airport_iata", nullable = false, length = 3)
    val departureAirportIata: String,

    @Column(name = "arrival_airport_iata", nullable = false, length = 3)
    val arrivalAirportIata: String,

    @Column(name = "departure_time", nullable = false)
    val departureTime: Instant,

    @Column(name = "arrival_time", nullable = false)
    val arrivalTime: Instant,

    @Column(name = "total_seats", nullable = false)
    val totalSeats: Int,

    @Column(name = "available_seats", nullable = false)
    var availableSeats: Int,

    @Column(name = "ticket_price", nullable = false)
    val ticketPrice: Long,

    @Column(nullable = false)
    val status: String,
)
