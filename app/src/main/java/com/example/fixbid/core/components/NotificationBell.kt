package com.example.fixbid.core.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * Notification bell with an unread badge. Used on both customer and worker home
 * headers so the count stays consistent and Material-aligned.
 */
@Composable
fun NotificationBell(
    unreadCount: Int,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onPrimary
) {
    IconButton(onClick = onClick) {
        if (unreadCount > 0) {
            BadgedBox(
                badge = {
                    Badge {
                        Text(
                            text = if (unreadCount > 99) "99+" else "$unreadCount",
                            fontSize = 9.sp
                        )
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Thông báo ($unreadCount chưa đọc)",
                    tint = tint
                )
            }
        } else {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Thông báo",
                tint = tint
            )
        }
    }
}
