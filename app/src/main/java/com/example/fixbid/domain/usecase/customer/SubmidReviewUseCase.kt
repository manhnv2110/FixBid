package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.ReviewRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import javax.inject.Inject

/**
 * Submits a customer review for a completed booking and notifies the worker.
 * The customer id is resolved from the current session so callers only supply
 * booking/worker context, the rating, and optional comment/images.
 */
class SubmitReviewUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val authRepository: AuthRepository,
    private val sendNotification: SendNotificationUseCase
) {
    suspend operator fun invoke(
        bookingId: String,
        workerId: String,
        rating: Int,
        comment: String?,
        imageUrls: List<String> = emptyList()
    ): Resource<Review> {
        if (rating !in 1..5)
            return Resource.Error("Đánh giá phải từ 1 đến 5 sao")

        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")

        val review = Review(
            id = "",
            bookingId = bookingId,
            customerId = user.id,
            workerId = workerId,
            rating = rating,
            comment = comment?.trim()?.ifBlank { null },
            imageUrls = imageUrls,
            workerReply = null,
            createdAt = 0L
        )

        val result = reviewRepository.createReview(review)

        // Notify the worker about the new review. Non-fatal on failure.
        if (result is Resource.Success) {
            sendNotification(
                NotificationContentFactory.newReviewForWorker(
                    workerId = workerId,
                    bookingId = bookingId,
                    rating = rating,
                    customerName = user.fullName
                )
            )
        }
        return result
    }

    /** Upload a single review image, returning its public URL. */
    suspend fun uploadImage(
        bookingId: String,
        imageBytes: ByteArray,
        fileName: String
    ): Resource<String> = reviewRepository.uploadReviewImage(bookingId, imageBytes, fileName)
}
