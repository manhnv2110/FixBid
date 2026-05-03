package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review

interface ReviewRepository {
    suspend fun createReview(review: Review): Resource<Review>
    suspend fun getReviewsForWorker(workerId: String, page: Int = 0): Resource<List<Review>>
    suspend fun getReviewByBooking(bookingId: String): Resource<Review?>
    suspend fun replyToReview(reviewId: String, reply: String): Resource<Review>
}