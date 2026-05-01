package com.example.fixbid.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.model.AppNotification
import com.example.fixbid.ui.utils.NotificationIconMapper

@Composable
fun NotificationCard(
    notifications: List<AppNotification>,
    modifier: Modifier = Modifier
) {
    if (notifications.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    var isVisible by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val notification = notifications[currentIndex]
        val accentColor = Color(NotificationIconMapper.getAccentColor(notification.type))
        val bgColor = Color(NotificationIconMapper.getBackgroundColor(notification.type))
        val iconRes = NotificationIconMapper.getIcon(notification.type)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = NotificationIconMapper.getIcon(notification.type),
                            contentDescription = notification.label,
                            tint = Color(NotificationIconMapper.getAccentColor(notification.type)),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = notification.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = notification.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height((8.dp)))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NotifInfoChip(
                            icon = Icons.Outlined.CalendarMonth,
                            text = notification.date,
                            color = accentColor
                        )
                        NotifInfoChip(
                            icon = Icons.Outlined.Schedule,
                            text = notification.time,
                            color = accentColor
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val nextIndex = currentIndex + 1
                        if (nextIndex < notifications.size) {
                            currentIndex = nextIndex
                        } else {
                            isVisible = false
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Xem thêm",
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotifInfoChip(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = color.copy(alpha = 0.7f)
        )
    }
}