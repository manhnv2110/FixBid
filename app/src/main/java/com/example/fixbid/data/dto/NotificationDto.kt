package com.example.fixbid.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val label: String,
    val title: String,
    @SerialName("scheduled_date") val scheduledDate: String?,
    @SerialName("scheduled_time") val scheduledTime: String?,
    @SerialName("is_read") val isRead: Boolean
)