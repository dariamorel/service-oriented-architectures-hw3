package com.example.bookingservice

import com.example.bookingservice.api.BookingsApi
import com.example.bookingservice.dto.BookingResponse
import com.example.bookingservice.dto.CreateBookingRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class BookingsApiController(
    private val bookingAppService: BookingAppService,
) : BookingsApi {

    override fun createBooking(createBookingRequest: CreateBookingRequest): ResponseEntity<BookingResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(bookingAppService.create(createBookingRequest))

    override fun cancelBooking(id: UUID): ResponseEntity<BookingResponse> =
        ResponseEntity.ok(bookingAppService.cancel(id))

    override fun getBooking(id: UUID): ResponseEntity<BookingResponse> =
        ResponseEntity.ok(bookingAppService.get(id))

    override fun listBookings(userId: UUID): ResponseEntity<List<BookingResponse>> =
        ResponseEntity.ok(bookingAppService.listByUser(userId))
}
