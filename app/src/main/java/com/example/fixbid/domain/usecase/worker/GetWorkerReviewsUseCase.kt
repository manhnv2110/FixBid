package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.ReviewRepository
import javax.inject.Inject

/**
 * Loads the reviews left for the signed-in worker (paged), used by the worker
 * "Đánh giá của tôi" screen.
 */
class GetWorkerReviewsUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(page: Int = 0): Resource<List<Review>> {
        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")
        return reviewRepository.getReviewsForWorker(user.id, page)
    }

    suspend fun reply(reviewId: String, reply: String): Resource<Review> {
        if (reply.isBlank()) return Resource.Error("Nội dung phản hồi không được để trống")
        return reviewRepository.replyToReview(reviewId, reply.trim())
    }
}
