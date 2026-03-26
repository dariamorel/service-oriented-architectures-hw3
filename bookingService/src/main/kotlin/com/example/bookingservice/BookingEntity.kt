package com.example.bookingservice

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bookings")
class BookingEntity(
    @Id
    @Column(columnDefinition = "uuid")
    val id: UUID,

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    val userId: UUID,

    @Column(name = "flight_id", columnDefinition = "uuid")
    val flightId: UUID?,

    @Column(name = "passenger_name", nullable = false)
    val passengerName: String,

    @Column(name = "passenger_email", nullable = false)
    val passengerEmail: String,

    @Column(name = "seat_count", nullable = false)
    val seatCount: Int,

    @Column(name = "total_price", nullable = false)
    val totalPrice: Long,

    @Column(name = "reservation_id", columnDefinition = "uuid")
    val reservationId: UUID?,

    @Column(nullable = false)
    var status: String,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
)
