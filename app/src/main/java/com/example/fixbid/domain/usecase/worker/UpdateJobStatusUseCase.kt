package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import javax.inject.Inject

class UpdateJobStatusUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val sendNotification: SendNotificationUseCase
) {
    suspend operator fun invoke(
        bookingId: String,
        newStatus: BookingStatus,
        workerNote: String? = null
    ): Resource<Booking> {
        val result = when (newStatus) {
            BookingStatus.CONFIRMED    -> bookingRepository.confirmBooking(bookingId)
            BookingStatus.IN_PROGRESS  -> bookingRepository.startJob(bookingId)
            BookingStatus.COMPLETED    -> bookingRepository.completeJob(bookingId, workerNote)
            BookingStatus.PENDING_COMPLETION -> bookingRepository.completeJob(bookingId, workerNote)
            else -> Resource.Error("Trạng thái không hợp lệ")
        }

        // Mirror the status change to the customer as a notification. Non-fatal.
        if (result is Resource.Success) {
            notifyCustomer(result.data, newStatus)
        }
        return result
    }

    private suspend fun notifyCustomer(booking: Booking, newStatus: BookingStatus) {
        val customerId = booking.customerId.takeIf { it.isNotBlank() } ?: return
        val categoryName = booking.category.displayName
        val content = when (newStatus) {
            BookingStatus.CONFIRMED -> NotificationContentFactory.bookingConfirmedForCustomer(
                customerId = customerId, bookingId = booking.id, categoryName = categoryName
            )
            BookingStatus.IN_PROGRESS -> NotificationContentFactory.jobStartedForCustomer(
                customerId = customerId, bookingId = booking.id, categoryName = categoryName
            )
            BookingStatus.PENDING_COMPLETION,
            BookingStatus.COMPLETED -> NotificationContentFactory.jobCompletedForCustomer(
                customerId = customerId, bookingId = booking.id, categoryName = categoryName
            )
            else -> return
        }
        sendNotification(content)
    }
}
