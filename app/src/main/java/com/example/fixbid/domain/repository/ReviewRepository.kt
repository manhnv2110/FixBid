package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review

interface ReviewRepository {
    suspend fun createReview(review: Review): Resource<Review>
    suspend fun getReviewsForWorker(workerId: String, page: Int = 0): Resource<List<Review>>
    suspend fun getReviewByBooking(bookingId: String): Resource<Review?>
    suspend fun replyToReview(reviewId: String, reply: String): Resource<Review>

    /** Upload an image attached to a review; returns its public URL. */
    suspend fun uploadReviewImage(
        bookingId: String,
        imageBytes: ByteArray,
        fileName: String
    ): Resource<String>
}
