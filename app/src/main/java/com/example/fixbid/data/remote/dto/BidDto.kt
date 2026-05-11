package com.example.fixbid.data.remote.dto

import com.example.fixbid.core.utils.toEpochMillis
import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.BidStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BidDto(
    val id: String = "",
    @SerialName("booking_id")               val bookingId: String = "",
    @SerialName("worker_id")                val workerId: String = "",
    @SerialName("proposed_price")           val proposedPrice: Double = 0.0,
    @SerialName("estimated_duration_hours") val estimatedDurationHours: Double = 1.0,
    val message: String = "",
    val status: String = "pending",
    @SerialName("created_at")               val createdAt: String = "",
    @SerialName("updated_at")               val updatedAt: String = ""
) {
    fun toDomain() = Bid(
        id                     = id,
        bookingId              = bookingId,
        workerId               = workerId,
        proposedPrice          = proposedPrice,
        estimatedDurationHours = estimatedDurationHours,
        message                = message,
        status                 = runCatching { BidStatus.valueOf(status.uppercase()) }
            .getOrDefault(BidStatus.PENDING),
        createdAt              = createdAt.toEpochMillis()
    )
}

fun Bid.toDto() = BidDto(
    id                     = id,
    bookingId              = bookingId,
    workerId               = workerId,
    proposedPrice          = proposedPrice,
    estimatedDurationHours = estimatedDurationHours,
    message                = message,
    status                 = status.name.lowercase()
)