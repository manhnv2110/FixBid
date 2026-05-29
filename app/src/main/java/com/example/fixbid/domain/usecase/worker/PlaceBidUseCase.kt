package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.BidStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BidRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import javax.inject.Inject

class PlaceBidUseCase @Inject constructor(
    private val bidRepository: BidRepository,
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository,
    private val sendNotification: SendNotificationUseCase
) {
    /**
     * Đặt thầu cho một booking. Worker được lấy tự động từ phiên hiện tại.
     */
    suspend operator fun invoke(
        bookingId: String,
        proposedPrice: Double,
        estimatedDurationHours: Double,
        message: String
    ): Resource<Bid> {
        if (proposedPrice <= 0)
            return Resource.Error("Giá đặt thầu phải lớn hơn 0")
        if (estimatedDurationHours <= 0)
            return Resource.Error("Thời gian dự kiến phải lớn hơn 0")
        if (message.isBlank())
            return Resource.Error("Vui lòng nhập lời giới thiệu")

        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")

        val now = System.currentTimeMillis()
        val bid = Bid(
            id = "",
            bookingId = bookingId,
            workerId = user.id,
            proposedPrice = proposedPrice,
            estimatedDurationHours = estimatedDurationHours,
            message = message.trim(),
            status = BidStatus.PENDING,
            createdAt = now
        )
        val result = bidRepository.placeBid(bid)

        // Notify the customer that a new bid arrived. Non-fatal on failure.
        if (result is Resource.Success) {
            (bookingRepository.getBookingById(bookingId) as? Resource.Success)?.data?.let { booking ->
                sendNotification(
                    NotificationContentFactory.bidReceivedForCustomer(
                        customerId = booking.customerId,
                        bookingId = bookingId,
                        workerName = user.fullName,
                        priceLabel = formatCurrencyVnd(proposedPrice)
                    )
                )
            }
        }
        return result
    }
}
