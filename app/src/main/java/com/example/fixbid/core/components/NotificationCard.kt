package com.example.fixbid.core.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.core.utils.NotificationIconMapper
import com.example.fixbid.core.utils.toRelativeTime
import com.example.fixbid.domain.model.Notification

/**
 * Carousel-style work notification card shown on the customer home screen,
 * just below the promo banner.
 *
 * Layout choices:
 *  - Left: a circular colored icon badge that anchors the type at a glance.
 *  - Middle: a small Vietnamese type label, the title and body in a tight
 *    text stack so the user can scan the most important information first.
 *  - Right: a paging indicator ("1 / N") and a forward arrow so the user
 *    knows they can step through unread notifications without guessing.
 *
 * The previous design crammed icon + label + title + body + time into a
 * loose column and relied on a single chevron on the right. The new layout
 * prioritises hierarchy and makes the carousel affordance discoverable.
 */
@Composable
fun NotificationCard(
    notifications: List<Notification>,
    modifier: Modifier = Modifier
) {
    if (notifications.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    var isVisible    by remember { mutableStateOf(true) }

    // Reset to the first item when the underlying list changes.
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
        val typeLabel    = NotificationIconMapper.getLabel(notification.type)

        Card(
            modifier  = modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(18.dp),
            colors    = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Leading icon badge ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = NotificationIconMapper.getIcon(notification.type),
                        contentDescription = typeLabel,
                        tint               = accentColor,
                        modifier           = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // ── Content stack ─────────────────────────────────────
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypePill(label = typeLabel, accentColor = accentColor)
                        Text(
                            text  = notification.createdAt.toRelativeTime(),
                            fontSize = 11.sp,
                            color = accentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text       = notification.title,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = accentColor,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )

                    if (notification.body.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text     = notification.body,
                            fontSize = 12.sp,
                            color    = accentColor.copy(alpha = 0.78f),
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ── Trailing pagination + advance control ─────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (notifications.size > 1) {
                        Text(
                            text  = "${currentIndex + 1}/${notifications.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor.copy(alpha = 0.75f)
                        )
                    }
                    IconButton(
                        onClick = {
                            val next = currentIndex + 1
                            if (next < notifications.size) currentIndex = next
                            else isVisible = false
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (notifications.size > 1) "Tiếp theo" else "Đóng",
                            tint               = accentColor,
                            modifier           = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypePill(label: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accentColor.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
            maxLines = 1
        )
    }
}
