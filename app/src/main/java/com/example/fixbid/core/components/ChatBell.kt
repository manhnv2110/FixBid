package com.example.fixbid.core.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
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
 * Chat icon with an unread-message badge. Mirrors [NotificationBell] so headers
 * stay visually consistent across customer and worker screens.
 */
@Composable
fun ChatBell(
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
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Tin nhắn ($unreadCount chưa đọc)",
                    tint = tint
                )
            }
        } else {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Tin nhắn",
                tint = tint
            )
        }
    }
}
