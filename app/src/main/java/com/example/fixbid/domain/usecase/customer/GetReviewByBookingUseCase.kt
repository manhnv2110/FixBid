package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review
import com.example.fixbid.domain.repository.ReviewRepository
import javax.inject.Inject

/** Returns the existing review for a booking, or null if none yet. */
class GetReviewByBookingUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    suspend operator fun invoke(bookingId: String): Resource<Review?> =
        reviewRepository.getReviewByBooking(bookingId)
}
