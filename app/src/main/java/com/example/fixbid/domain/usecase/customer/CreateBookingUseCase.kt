package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingType
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import javax.inject.Inject

class CreateBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val sendNotification: SendNotificationUseCase
) {
    suspend operator fun invoke(booking: Booking): Resource<Booking> {
        val result = when (booking.type) {
            BookingType.DIRECT  -> bookingRepository.createDirectBooking(booking)
            BookingType.BIDDING -> bookingRepository.createBiddingBooking(booking)
        }

        // For a direct booking the worker is known up-front — notify them about
        // the new job request so they can respond quickly. Non-fatal on failure.
        if (result is Resource.Success && booking.type == BookingType.DIRECT) {
            val created = result.data
            val workerId = created.workerId.takeIf { it.isNotBlank() }
            if (workerId != null) {
                sendNotification(
                    NotificationContentFactory.bookingRequestForWorker(
                        workerId = workerId,
                        bookingId = created.id,
                        categoryName = created.category.displayName
                    )
                )
            }
        }
        return result
    }
}
