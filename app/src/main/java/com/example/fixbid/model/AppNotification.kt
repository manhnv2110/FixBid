package com.example.fixbid.model

import androidx.annotation.DrawableRes

enum class NotificationType {
    UPCOMING_TASK,
    INVOICE,
}

data class AppNotification(
    val id: String,
    val type: NotificationType,
    val label: String,
    val title: String,
    val date: String,
    val time: String,
)