package com.example.bookingservice

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BookingRepository : JpaRepository<BookingEntity, UUID> {
    fun findAllByUserId(userId: UUID): List<BookingEntity>
}
