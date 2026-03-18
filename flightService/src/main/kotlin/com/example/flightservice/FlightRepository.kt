package com.example.flightservice

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.time.Instant
import java.util.UUID

interface FlightRepository : JpaRepository<FlightEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FlightEntity f where f.id = :id")
    fun findForUpdate(id: UUID): FlightEntity?

    @Query(
        """
        select f
        from FlightEntity f
        where f.status = :status
          and f.departureAirportIata = :departureAirportIata
          and f.arrivalAirportIata = :arrivalAirportIata
        """,
    )
    fun findExistingFlights(
        status: String,
        departureAirportIata: String,
        arrivalAirportIata: String,
    ): List<FlightEntity>

    @Query(
        """
        select f
        from FlightEntity f
        where f.status = :status
          and f.departureAirportIata = :departureAirportIata
          and f.arrivalAirportIata = :arrivalAirportIata
          and f.departureDate = :departureDate
        """,
    )
    fun findExistingFlightsWithDepartureDate(
        status: String,
        departureAirportIata: String,
        arrivalAirportIata: String,
        departureDate: Instant,
    ): List<FlightEntity>
}
