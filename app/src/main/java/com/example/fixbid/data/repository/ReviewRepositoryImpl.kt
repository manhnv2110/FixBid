package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.ReviewDto
import com.example.fixbid.data.remote.dto.toDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review
import com.example.fixbid.domain.repository.ReviewRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class ReviewRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : ReviewRepository {

    override suspend fun createReview(review: Review): Resource<Review> = runCatching {
        val result = client.from(Tables.REVIEWS)
            .insert(review.toDto()) { select() }
            .decodeSingle<ReviewDto>()
        // Trigger update_worker_rating trong DB tự cập nhật average_rating
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Gửi đánh giá thất bại") }

    override suspend fun getReviewsForWorker(
        workerId: String,
        page: Int
    ): Resource<List<Review>> = runCatching {
        val pageSize = 10
        val result = client.from(Tables.REVIEWS)
            .select {
                filter { eq("worker_id", workerId) }
                order("created_at", Order.DESCENDING)
                range(
                    from = (page * pageSize).toLong(),
                    to   = ((page + 1) * pageSize - 1).toLong()
                )
            }
            .decodeList<ReviewDto>()
        Resource.Success(result.map { it.toDomain() })
    }.getOrElse { Resource.Error(it.message ?: "Lỗi tải đánh giá") }

    override suspend fun getReviewByBooking(bookingId: String): Resource<Review?> =
        runCatching {
            val result = client.from(Tables.REVIEWS)
                .select { filter { eq("booking_id", bookingId) } }
                .decodeList<ReviewDto>()
            Resource.Success(result.firstOrNull()?.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Lỗi") }

    override suspend fun replyToReview(
        reviewId: String,
        reply: String
    ): Resource<Review> = runCatching {
        val result = client.from(Tables.REVIEWS)
            .update(buildJsonObject { put("worker_reply", reply) }) {
                filter { eq("id", reviewId) }
                select()
            }
            .decodeSingle<ReviewDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Trả lời đánh giá thất bại") }
}