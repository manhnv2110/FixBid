package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.core.utils.formatCurrencyVnd
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
 *
 * Note: With the introduction of the QUOTED stage, the canonical path is for the
 * worker to send a price quote first ([QuoteDirectBookingUseCase]) and wait for
 * the customer to accept. This use case is kept for backwards compatibility and
 * for cases where the booking already has an agreed_price (e.g. legacy data or
 * a previously accepted quote that still needs the status flipped).
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

/**
 * Worker sends a price quote on a direct booking. This is the canonical entry
 * point for the direct-booking flow now that we've inserted a QUOTED stage
 * between PENDING and AWAITING_PAYMENT — the worker proposes a price + duration,
 * the booking moves to QUOTED, and the customer gets a notification with the
 * price so they can accept (→ AWAITING_PAYMENT) or reject (→ back to PENDING).
 *
 * Validation:
 *   - price must be > 0 (also enforced by the DB CHECK constraint)
 *   - message must be at least 10 chars (consistent with bid messages)
 */
class QuoteDirectBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val sendNotification: SendNotificationUseCase
) {
    suspend operator fun invoke(
        bookingId: String,
        proposedPrice: Double,
        message: String,
        estimatedDurationHours: Double?
    ): Resource<Booking> {
        if (proposedPrice <= 0.0) return Resource.Error("Giá báo phải lớn hơn 0")
        val trimmedMessage = message.trim()
        if (trimmedMessage.length < 10) {
            return Resource.Error("Lời giới thiệu cần ít nhất 10 ký tự")
        }

        val result = bookingRepository.quoteDirectBooking(
            bookingId = bookingId,
            proposedPrice = proposedPrice,
            message = trimmedMessage,
            estimatedDurationHours = estimatedDurationHours
        )
        if (result is Resource.Success) {
            val booking = result.data
            booking.customerId.takeIf { it.isNotBlank() }?.let { customerId ->
                sendNotification(
                    NotificationContentFactory.directBookingQuotedForCustomer(
                        customerId = customerId,
                        bookingId = booking.id,
                        categoryName = booking.category.displayName,
                        workerName = booking.worker?.fullName,
                        priceLabel = formatCurrencyVnd(proposedPrice)
                    )
                )
            }
        }
        return result
    }
}
