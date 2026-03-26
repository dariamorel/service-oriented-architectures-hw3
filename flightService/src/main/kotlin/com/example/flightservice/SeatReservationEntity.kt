package com.example.flightservice

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "seat_reservations")
class SeatReservationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    val flight: FlightEntity,

    @Column(name = "booking_id", nullable = false, unique = true, columnDefinition = "uuid")
    val bookingId: UUID,

    @Column(name = "reserved_seats", nullable = false)
    val reservedSeats: Int,

    @Column(nullable = false)
    var status: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "released_at")
    var releasedAt: Instant? = null,
)
