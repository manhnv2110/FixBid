package com.example.fixbid.ui.utils

import com.example.fixbid.domain.model.NotificationType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.ui.graphics.vector.ImageVector

object NotificationIconMapper {
    fun getIcon(type: NotificationType): ImageVector = when (type) {
        NotificationType.UPCOMING_TASK -> Icons.Outlined.Assignment
        NotificationType.INVOICE -> Icons.Outlined.Assignment
    }

    fun getLabel(type: NotificationType): String = when (type) {
        NotificationType.UPCOMING_TASK -> "Nhiệm vụ sắp tới"
        NotificationType.INVOICE       -> "Hóa đơn"
    }

    fun getAccentColor(type: NotificationType): Long = when (type) {
        NotificationType.UPCOMING_TASK -> 0xFF1565C0
        NotificationType.INVOICE       -> 0xFF2E7D32
    }

    fun getBackgroundColor(type: NotificationType): Long = when (type) {
        NotificationType.UPCOMING_TASK -> 0xFFE3F0FF
        NotificationType.INVOICE       -> 0xFFE8F5E9
    }
}