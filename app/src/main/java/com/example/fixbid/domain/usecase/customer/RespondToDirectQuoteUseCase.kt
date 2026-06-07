package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import javax.inject.Inject

/**
 * Customer accepts the worker's quote on a direct booking. The repository
 * copies the quoted price into `agreed_price` and flips the status to
 * AWAITING_PAYMENT so the payment screen can render with a non-null amount.
 * The worker is notified so they know to expect payment.
 */
class AcceptDirectQuoteUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val sendNotification: SendNotificationUseCase
) {
    suspend operator fun invoke(bookingId: String): Resource<Booking> {
        val result = bookingRepository.acceptDirectQuote(bookingId)
        if (result is Resource.Success) {
            val booking = result.data
            booking.workerId.takeIf { it.isNotBlank() }?.let { workerId ->
                sendNotification(
                    NotificationContentFactory.directQuoteAcceptedForWorker(
                        workerId = workerId,
                        bookingId = booking.id,
                        categoryName = booking.category.displayName
                    )
                )
            }
        }
        return result
    }
}

/**
 * Customer rejects the worker's quote with an optional reason. The booking
 * goes back to PENDING and the quote columns are cleared so the worker can
 * either send a new quote or decline the job. The reason is stored in
 * `worker_note` for the worker's reference and surfaced through a notification.
 */
class RejectDirectQuoteUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val sendNotification: SendNotificationUseCase
) {
    suspend operator fun invoke(bookingId: String, reason: String?): Resource<Booking> {
        val trimmed = reason?.trim()?.takeIf { it.isNotBlank() }
        val result = bookingRepository.rejectDirectQuote(bookingId, trimmed)
        if (result is Resource.Success) {
            val booking = result.data
            booking.workerId.takeIf { it.isNotBlank() }?.let { workerId ->
                sendNotification(
                    NotificationContentFactory.directQuoteRejectedForWorker(
                        workerId = workerId,
                        bookingId = booking.id,
                        categoryName = booking.category.displayName,
                        reason = trimmed
                    )
                )
            }
        }
        return result
    }
}
