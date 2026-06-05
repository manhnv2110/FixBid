package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import kotlinx.coroutines.flow.Flow

interface BookingRepository {

    // Customer
    suspend fun createDirectBooking(booking: Booking): Resource<Booking>
    suspend fun createBiddingBooking(booking: Booking): Resource<Booking>
    suspend fun getCustomerBookings(customerId: String, status: BookingStatus? = null): Resource<List<Booking>>
    suspend fun cancelBooking(bookingId: String, reason: String): Resource<Unit>
    suspend fun deleteBooking(bookingId: String): Resource<Unit>
    suspend fun updateBooking(booking: Booking): Resource<Booking>

    // Worker
    suspend fun getWorkerBookings(workerId: String, status: BookingStatus? = null): Resource<List<Booking>>
    suspend fun getOpenJobRequests(categories: List<ServiceCategory>? = null, excludeBookingIds: List<String> = emptyList()): Resource<List<Booking>>

    /** PENDING + DIRECT bookings assigned to this worker — they must accept or decline. */
    suspend fun getPendingDirectBookings(workerId: String): Resource<List<Booking>>

    /** Worker accepts a DIRECT booking → status flips to AWAITING_PAYMENT so customer pays. */
    suspend fun acceptDirectBooking(bookingId: String): Resource<Booking>

    /** Worker declines a DIRECT booking → status=CANCELLED, reason saved in cancel_reason. */
    suspend fun declineDirectBooking(bookingId: String, reason: String): Resource<Booking>

    suspend fun confirmBooking(bookingId: String): Resource<Booking>
    suspend fun startJob(bookingId: String): Resource<Booking>
    suspend fun completeJob(bookingId: String, workerNote: String?): Resource<Booking>
    suspend fun submitJobCompletion(bookingId: String, completionNote: String?, completionImageUrls: List<String>): Resource<Booking>

    // Storage - upload completion images
    suspend fun uploadCompletionImage(bookingId: String, imageBytes: ByteArray, fileName: String): Resource<String>

    // Storage - upload description images (khi khách tạo yêu cầu)
    suspend fun uploadDescriptionImage(bookingId: String, imageBytes: ByteArray, fileName: String): Resource<String>
    suspend fun updateDescriptionImages(bookingId: String, imageUrls: List<String>): Resource<Booking>

    // Customer – completion confirmation
    suspend fun confirmCompletion(bookingId: String): Resource<Booking>
    suspend fun rejectCompletion(bookingId: String, reason: String): Resource<Booking>

    // Shared
    suspend fun getBookingById(bookingId: String): Resource<Booking>
    suspend fun updateBookingStatus(bookingId: String, status: String): Resource<Booking>
    fun observeBooking(bookingId: String): Flow<Booking?>
}