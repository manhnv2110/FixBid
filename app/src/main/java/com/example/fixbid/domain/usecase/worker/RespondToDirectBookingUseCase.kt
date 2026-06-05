package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingType
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import javax.inject.Inject

/**
 * Worker accepts a direct booking. Backend transitions status to AWAITING_PAYMENT
 * (the customer must pay next), and the customer is notified so they can act.
 *
 * Guarded so the call is a no-op if the booking isn't actually a DIRECT/PENDING
 * one belonging to this worker — protects against UI race conditions where the
 * status changed (e.g. customer cancelled) between list load and tap.
 */
class AcceptDirectBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val sendNotification: SendNotificationUseCase
) {
    suspend operator fun invoke(bookingId: String): Resource<Booking> {
        val result = bookingRepository.acceptDirectBooking(bookingId)
        if (result is Resource.Success) {
            val booking = result.data
            booking.customerId.takeIf { it.isNotBlank() }?.let { customerId ->
                sendNotification(
                    NotificationContentFactory.directBookingAcceptedForCustomer(
                        customerId = customerId,
                        bookingId = booking.id,
                        categoryName = booking.category.displayName,
                        workerName = booking.worker?.fullName
                    )
                )
            }
        }
        return result
    }
}

/**
 * Worker declines a direct booking. The reason is stored in `cancel_reason`
 * (not `customer_note`) so the customer's contact details stay intact, and
 * the customer is notified so they can route the work to another worker.
 */
class DeclineDirectBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val sendNotification: SendNotificationUseCase
) {
    suspend operator fun invoke(bookingId: String, reason: String): Resource<Booking> {
        val trimmed = reason.trim().ifBlank { "Thợ không thể nhận đơn này." }
        val result = bookingRepository.declineDirectBooking(bookingId, trimmed)
        if (result is Resource.Success) {
            val booking = result.data
            // Only notify when the booking really was DIRECT — prevents a duplicate
            // notification if the same method is ever reused for bidding cleanup.
            if (booking.type == BookingType.DIRECT) {
                booking.customerId.takeIf { it.isNotBlank() }?.let { customerId ->
                    sendNotification(
                        NotificationContentFactory.directBookingDeclinedForCustomer(
                            customerId = customerId,
                            bookingId = booking.id,
                            categoryName = booking.category.displayName,
                            reason = trimmed
                        )
                    )
                }
            }
        }
        return result
    }
}
