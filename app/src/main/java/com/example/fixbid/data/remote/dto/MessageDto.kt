package com.example.fixbid.data.remote.dto

import com.example.fixbid.domain.model.Message
import com.example.fixbid.domain.model.MessageType
import com.example.fixbid.core.utils.toEpochMillis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("sender_id") val senderId: String = "",
    val content: String = "",
    val type: String = "text",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
) {
    fun toDomain() = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        content = content,
        type = runCatching { MessageType.valueOf(type.uppercase()) }
            .getOrDefault(MessageType.TEXT),
        imageUrl = imageUrl,
        isRead = isRead,
        createdAt = createdAt.toEpochMillis()
    )
}