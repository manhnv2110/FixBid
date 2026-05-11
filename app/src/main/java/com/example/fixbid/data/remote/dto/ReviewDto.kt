package com.example.fixbid.data.remote.dto

import com.example.fixbid.core.utils.toEpochMillis
import com.example.fixbid.domain.model.Review
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReviewDto(
    val id: String = "",
    @SerialName("booking_id")   val bookingId: String = "",
    @SerialName("customer_id")  val customerId: String = "",
    @SerialName("worker_id")    val workerId: String = "",
    val rating: Int = 5,
    val comment: String? = null,
    @SerialName("image_urls")   val imageUrls: List<String> = emptyList(),
    @SerialName("worker_reply") val workerReply: String? = null,
    @SerialName("created_at")   val createdAt: String = "",
    @SerialName("updated_at")   val updatedAt: String = ""
) {
    fun toDomain() = Review(
        id          = id,
        bookingId   = bookingId,
        customerId  = customerId,
        workerId    = workerId,
        rating      = rating,
        comment     = comment,
        imageUrls   = imageUrls,
        workerReply = workerReply,
        createdAt   = createdAt.toEpochMillis()
    )
}

fun Review.toDto() = ReviewDto(
    id         = id,
    bookingId  = bookingId,
    customerId = customerId,
    workerId   = workerId,
    rating     = rating,
    comment    = comment,
    imageUrls  = imageUrls,
    workerReply = workerReply
)