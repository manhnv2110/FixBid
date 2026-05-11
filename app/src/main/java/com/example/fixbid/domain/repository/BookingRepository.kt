package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import kotlinx.coroutines.flow.Flow

interface BookingRepository {

    // Customer
    suspend fun createDirectBooking(booking: Booking): Resource<Booking>
    suspend fun createBiddingBooking(booking: Booking): Resource<Booking>
    suspend fun getCustomerBookings(
        customerId: String,
        status: BookingStatus? = null
    ): Resource<List<Booking>>
    suspend fun cancelBooking(bookingId: String, reason: String): Resource<Unit>

    // Worker
    suspend fun getWorkerBookings(
        workerId: String,
        status: BookingStatus? = null
    ): Resource<List<Booking>>
    suspend fun confirmBooking(bookingId: String): Resource<Booking>
    suspend fun startJob(bookingId: String): Resource<Booking>
    suspend fun completeJob(bookingId: String, workerNote: String?): Resource<Booking>

    // Shared
    suspend fun getBookingById(bookingId: String): Resource<Booking>
    fun observeBooking(bookingId: String): Flow<Booking?>
}