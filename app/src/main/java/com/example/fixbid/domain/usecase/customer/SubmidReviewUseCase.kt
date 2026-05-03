package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review
import com.example.fixbid.domain.repository.ReviewRepository
import javax.inject.Inject

class SubmitReviewUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    suspend operator fun invoke(review: Review): Resource<Review> {
        if (review.rating !in 1..5)
            return Resource.Error("Đánh giá phải từ 1 đến 5 sao")
        return reviewRepository.createReview(review)
    }
}