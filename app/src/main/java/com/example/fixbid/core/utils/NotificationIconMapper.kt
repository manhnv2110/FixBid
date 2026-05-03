package com.example.fixbid.core.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.fixbid.domain.model.NotificationType

object NotificationIconMapper {

    fun getIcon(type: NotificationType): ImageVector = when (type) {
        NotificationType.BOOKING_REQUEST   -> Icons.AutoMirrored.Outlined.Assignment
        NotificationType.BOOKING_CONFIRMED -> Icons.Outlined.CheckCircle
        NotificationType.BOOKING_CANCELLED -> Icons.Outlined.Cancel
        NotificationType.BID_RECEIVED      -> Icons.Outlined.Gavel
        NotificationType.BID_ACCEPTED      -> Icons.Outlined.ThumbUp
        NotificationType.PAYMENT_RECEIVED  -> Icons.Outlined.Payments
        NotificationType.NEW_MESSAGE       -> Icons.Outlined.Chat
        NotificationType.NEW_REVIEW        -> Icons.Outlined.Star
        NotificationType.SYSTEM            -> Icons.Outlined.Info
    }

    fun getAccentColor(type: NotificationType): Long = when (type) {
        NotificationType.BOOKING_REQUEST,
        NotificationType.BOOKING_CONFIRMED  -> 0xFF1565C0
        NotificationType.BOOKING_CANCELLED  -> 0xFFC62828
        NotificationType.BID_RECEIVED,
        NotificationType.BID_ACCEPTED       -> 0xFF6A1B9A
        NotificationType.PAYMENT_RECEIVED   -> 0xFF2E7D32
        NotificationType.NEW_MESSAGE        -> 0xFF00838F
        NotificationType.NEW_REVIEW         -> 0xFFF57F17
        NotificationType.SYSTEM             -> 0xFF546E7A
    }

    fun getBackgroundColor(type: NotificationType): Long = when (type) {
        NotificationType.BOOKING_REQUEST,
        NotificationType.BOOKING_CONFIRMED  -> 0xFFE3F0FF
        NotificationType.BOOKING_CANCELLED  -> 0xFFFFEBEE
        NotificationType.BID_RECEIVED,
        NotificationType.BID_ACCEPTED       -> 0xFFF3E5F5
        NotificationType.PAYMENT_RECEIVED   -> 0xFFE8F5E9
        NotificationType.NEW_MESSAGE        -> 0xFFE0F7FA
        NotificationType.NEW_REVIEW         -> 0xFFFFFDE7
        NotificationType.SYSTEM             -> 0xFFECEFF1
    }
}