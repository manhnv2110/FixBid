package com.example.fixbid.data.remote.dto

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.BookingType
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.core.utils.toEpochMillis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BookingDto(
    val id: String = "",
    @SerialName("customer_id") val customerId: String = "",
    @SerialName("worker_id") val workerId: String? = null,
    val category: String = "",
    val description: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("estimated_duration_hours") val estimatedDurationHours: Double = 1.0,
    val status: String = "pending",
    val type: String = "direct",
    @SerialName("agreed_price") val agreedPrice: Double? = null,
    @SerialName("customer_note") val customerNote: String? = null,
    @SerialName("worker_note") val workerNote: String? = null,
    @SerialName("description_images") val descriptionImages: List<String>? = null,
    @SerialName("completion_note") val completionNote: String? = null,
    @SerialName("completion_images") val completionImages: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toDomain() = Booking(
        id = id,
        customerId = customerId,
        workerId = workerId ?: "",
        category = runCatching { ServiceCategory.valueOf(category.uppercase()) }
            .getOrDefault(ServiceCategory.OTHER),
        description = description,
        address = address,
        latitude = latitude,
        longitude = longitude,
        scheduledAt = scheduledAt?.toEpochMillis() ?: 0L,
        estimatedDurationHours = estimatedDurationHours,
        status = runCatching { BookingStatus.valueOf(status.uppercase()) }
            .getOrDefault(BookingStatus.PENDING),
        type = runCatching { BookingType.valueOf(type.uppercase()) }
            .getOrDefault(BookingType.DIRECT),
        agreedPrice = agreedPrice,
        customerNote = customerNote,
        workerNote = workerNote,
        descriptionImages = descriptionImages,
        completionNote = completionNote,
        completionImages = completionImages,
        createdAt = createdAt?.toEpochMillis() ?: 0L,
        updatedAt = updatedAt?.toEpochMillis() ?: 0L
    )
}

fun Booking.toDto() = BookingDto(
    id                     = id,
    customerId             = customerId,
    workerId               = workerId.ifBlank { null },
    category               = category.name.lowercase(),
    description            = description,
    address                = address,
    latitude               = latitude,
    longitude              = longitude,
    scheduledAt            = java.time.Instant.ofEpochMilli(scheduledAt).toString(),
    estimatedDurationHours = estimatedDurationHours,
    status                 = status.name.lowercase(),
    type                   = type.name.lowercase(),
    agreedPrice            = agreedPrice,
    customerNote           = customerNote,
    workerNote             = workerNote,
    descriptionImages      = descriptionImages?.ifEmpty { null },
    completionNote         = completionNote,
    completionImages       = completionImages?.ifEmpty { null }
)