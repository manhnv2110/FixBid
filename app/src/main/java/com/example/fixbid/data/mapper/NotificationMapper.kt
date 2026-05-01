package com.example.fixbid.data.mapper

import com.example.fixbid.data.dto.NotificationDto
import com.example.fixbid.model.AppNotification
import com.example.fixbid.model.NotificationType

fun NotificationDto.toModel() : AppNotification {
    val type = when (this.type) {
        "UPCOMING_TASK" -> NotificationType.UPCOMING_TASK
        else -> NotificationType.INVOICE
    }
    return AppNotification(
        id = this.id,
        type = type,
        label = this.label,
        title = this.title,
        date = this.scheduledDate ?: "",
        time = this.scheduledTime ?: ""
    )
}