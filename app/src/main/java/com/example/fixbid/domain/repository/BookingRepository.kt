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

    /**
     * Worker sends a price quote on a DIRECT booking. Status moves to QUOTED so
     * the customer can review and accept/reject the price before payment. The
     * proposed price + message + duration are persisted in the dedicated
     * `quoted_*` columns; `agreed_price` is only set later when the customer
     * accepts the quote (see [acceptDirectQuote]).
     */
    suspend fun quoteDirectBooking(
        bookingId: String,
        proposedPrice: Double,
        message: String,
        estimatedDurationHours: Double?
    ): Resource<Booking>

    /**
     * Customer accepts the worker's quote: backend copies `quoted_price` into
     * `agreed_price` and transitions the booking to AWAITING_PAYMENT so the
     * payment screen can render with a non-null amount.
     */
    suspend fun acceptDirectQuote(bookingId: String): Resource<Booking>

    /**
     * Customer rejects the quote with an optional reason. The booking goes
     * back to PENDING and the quote columns are cleared so the worker can
     * either send a new quote or decline the job entirely. The reason is
     * stored in `worker_note` so the worker sees actionable feedback when
     * they reopen the request.
     */
    suspend fun rejectDirectQuote(bookingId: String, reason: String?): Resource<Booking>

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