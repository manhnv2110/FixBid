package com.example.fixbid.data.remote.dto

import com.example.fixbid.core.utils.toEpochMillis
import com.example.fixbid.domain.model.CallStatus
import com.example.fixbid.domain.model.VideoCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoCallDto(
    val id: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("caller_id") val callerId: String = "",
    @SerialName("callee_id") val calleeId: String = "",
    val status: String = "ringing",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null
) {
    fun toDomain() = VideoCall(
        id = id,
        conversationId = conversationId,
        callerId = callerId,
        calleeId = calleeId,
        status = CallStatus.fromRaw(status),
        startedAt = startedAt?.toEpochMillis() ?: 0L,
        answeredAt = answeredAt?.toEpochMillis(),
        endedAt = endedAt?.toEpochMillis(),
        durationSeconds = durationSeconds
    )
}
