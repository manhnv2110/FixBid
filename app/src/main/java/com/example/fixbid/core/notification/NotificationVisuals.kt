package com.example.fixbid.core.notification

import com.example.fixbid.core.utils.NotificationIconMapper
import com.example.fixbid.domain.model.NotificationType

/**
 * Bridges the Compose-oriented [NotificationIconMapper] (which exposes colors as
 * `Long` ARGB literals) to the plain `Int` colors the platform
 * NotificationCompat builder expects.
 */
object NotificationVisuals {
    fun accentColor(type: NotificationType): Int =
        NotificationIconMapper.getAccentColor(type).toInt()
}
