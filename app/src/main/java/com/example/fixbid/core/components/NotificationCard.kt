package com.example.fixbid.core.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.core.utils.NotificationIconMapper
import com.example.fixbid.core.utils.toRelativeTime
import com.example.fixbid.domain.model.Notification

@Composable
fun NotificationCard(
    notifications: List<Notification>,
    modifier: Modifier = Modifier
) {
    if (notifications.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    var isVisible    by remember { mutableStateOf(true) }

    // Reset về 0 khi danh sách thay đổi
    LaunchedEffect(notifications) {
        currentIndex = 0
        isVisible    = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter   = fadeIn() + expandVertically(),
        exit    = fadeOut() + shrinkVertically()
    ) {
        val notification = notifications[currentIndex]
        val accentColor  = Color(NotificationIconMapper.getAccentColor(notification.type))
        val bgColor      = Color(NotificationIconMapper.getBackgroundColor(notification.type))

        Card(
            modifier  = modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // ── Type label + icon ──────────────────────
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector        = NotificationIconMapper.getIcon(notification.type),
                            contentDescription = notification.type.name,
                            tint               = accentColor,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text(
                            text       = notification.type.name
                                .replace("_", " ")
                                .lowercase()
                                .replaceFirstChar { it.uppercase() },
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color      = accentColor.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ── Tiêu đề thông báo ──────────────────────
                    Text(
                        text       = notification.title,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = accentColor
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // ── Nội dung thông báo ─────────────────────
                    Text(
                        text     = notification.body,
                        fontSize = 12.sp,
                        color    = accentColor.copy(alpha = 0.7f),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // ── Thời gian tương đối ────────────────────
                    NotifInfoChip(
                        icon  = Icons.Outlined.Schedule,
                        text  = notification.createdAt.toRelativeTime(),
                        color = accentColor
                    )
                }

                // ── Nút next / đóng ───────────────────────────
                IconButton(
                    onClick = {
                        val next = currentIndex + 1
                        if (next < notifications.size) currentIndex = next
                        else isVisible = false
                    }
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tiếp theo",
                        tint               = accentColor,
                        modifier           = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotifInfoChip(
    icon:  ImageVector,
    text:  String,
    color: Color
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = color.copy(alpha = 0.7f),
            modifier           = Modifier.size(14.dp)
        )
        Text(
            text     = text,
            fontSize = 12.sp,
            color    = color.copy(alpha = 0.7f)
        )
    }
}