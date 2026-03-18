package com.example.flightservice

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SeatReservationRepository : JpaRepository<SeatReservationEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SeatReservationEntity r where r.bookingId = :bookingId and r.status = :status")
    fun findForUpdate(bookingId: UUID, status: String): SeatReservationEntity?
}
