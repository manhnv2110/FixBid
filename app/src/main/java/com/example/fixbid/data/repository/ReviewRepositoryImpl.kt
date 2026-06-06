package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.ReviewDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review
import com.example.fixbid.domain.repository.ReviewRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class ReviewRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : ReviewRepository {

    override suspend fun createReview(review: Review): Resource<Review> = runCatching {
        // Build the payload explicitly: the Supabase serializer omits fields equal
        // to their DTO default (encodeDefaults = false), which would silently drop
        // a required NOT NULL column such as `rating` when it equals its default
        // value (5) — causing a null violation. An explicit JSON object guarantees
        // every required column is sent, while letting the DB generate id/timestamps.
        val payload = buildJsonObject {
            put("booking_id", review.bookingId)
            put("customer_id", review.customerId)
            put("worker_id", review.workerId)
            put("rating", review.rating)
            review.comment?.let { put("comment", it) }
            put("image_urls", JsonArray(review.imageUrls.map { JsonPrimitive(it) }))
            review.workerReply?.let { put("worker_reply", it) }
        }
        val result = client.from(Tables.REVIEWS)
            .insert(payload) { select() }
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

    override suspend fun getReviewsByCustomer(customerId: String): Resource<List<Review>> =
        runCatching {
            val result = client.from(Tables.REVIEWS)
                .select { filter { eq("customer_id", customerId) } }
                .decodeList<ReviewDto>()
            Resource.Success(result.map { it.toDomain() })
        }.getOrElse { Resource.Error(it.message ?: "Lỗi tải danh sách đánh giá") }

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

    override suspend fun uploadReviewImage(
        bookingId: String,
        imageBytes: ByteArray,
        fileName: String
    ): Resource<String> = runCatching {
        val path = "reviews/$bookingId/$fileName"
        val bucket = client.storage.from("booking-images")
        bucket.upload(path, imageBytes) { upsert = true }
        Resource.Success(bucket.publicUrl(path))
    }.getOrElse { Resource.Error(it.message ?: "Upload ảnh đánh giá thất bại") }
}