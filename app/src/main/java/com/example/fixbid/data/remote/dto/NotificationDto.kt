package com.example.fixbid.data.remote.dto

import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.NotificationType
import com.example.fixbid.core.utils.toEpochMillis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String = "",
    @SerialName("user_id")      val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "system",
    @SerialName("reference_id") val referenceId: String? = null,
    @SerialName("is_read")      val isRead: Boolean = false,
    @SerialName("created_at")   val createdAt: String = ""
) {
    fun toDomain() = Notification(
        id          = id,
        userId      = userId,
        title       = title,
        body        = body,
        type        = runCatching {
            NotificationType.valueOf(type.uppercase())
        }.getOrDefault(NotificationType.SYSTEM),
        referenceId = if (referenceId != null) referenceId else null,
        isRead      = isRead,
        createdAt   = createdAt.toEpochMillis()
    )
}