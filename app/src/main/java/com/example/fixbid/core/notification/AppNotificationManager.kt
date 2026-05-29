package com.example.fixbid.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.fixbid.MainActivity
import com.example.fixbid.R
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.NotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the platform [NotificationManager] for FixBid: declares the channels,
 * maps a domain [Notification] to a system notification, and respects the
 * user's sound / vibration preferences.
 *
 * This is the in-app "push" surface — notifications are delivered over Supabase
 * Realtime while the app process is alive and surfaced here as heads-up system
 * notifications. (Background/killed-app delivery would additionally require FCM.)
 */
@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** High-importance channel for time-sensitive booking/job events. */
        const val CHANNEL_BOOKINGS = "fixbid_bookings"

        /** Default channel for chat + general updates. */
        const val CHANNEL_MESSAGES = "fixbid_messages"

        /** Reminders for upcoming scheduled services. */
        const val CHANNEL_REMINDERS = "fixbid_reminders"

        const val DEEPLINK_NOTIFICATION_ID = "notification_id"
        const val DEEPLINK_REFERENCE_ID = "reference_id"
        const val DEEPLINK_TYPE = "notification_type"
    }

    /** Create the channels. Safe to call repeatedly; call once at app startup. */
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val bookings = NotificationChannel(
            CHANNEL_BOOKINGS,
            "Đặt lịch & công việc",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Xác nhận đặt lịch, cập nhật công việc, thợ đang đến"
            enableVibration(true)
        }

        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            "Tin nhắn & cập nhật",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Tin nhắn mới và các cập nhật chung"
        }

        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            "Nhắc lịch hẹn",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Nhắc nhở trước các buổi hẹn dịch vụ"
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(bookings, messages, reminders))
    }

    fun hasPostPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Post a system notification for [notification], honoring the user's
     * [soundEnabled] / [vibrateEnabled] preferences. No-op if the runtime
     * permission is missing.
     */
    fun show(
        notification: Notification,
        soundEnabled: Boolean,
        vibrateEnabled: Boolean
    ) {
        if (!hasPostPermission()) return

        val channelId = channelFor(notification.type)
        val accent = NotificationVisuals.accentColor(notification.type)

        val contentIntent = PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DEEPLINK_NOTIFICATION_ID, notification.id)
                putExtra(DEEPLINK_REFERENCE_ID, notification.referenceId)
                putExtra(DEEPLINK_TYPE, notification.type.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setColor(accent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(priorityFor(notification.type))
            .setContentIntent(contentIntent)

        // Sound / vibration honoring user prefs. On O+ the channel governs these,
        // but we still set defaults for pre-O devices and to opt out cleanly.
        var defaults = 0
        if (soundEnabled) {
            val sound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(sound)
            defaults = defaults or NotificationCompat.DEFAULT_SOUND
        } else {
            builder.setSilent(!vibrateEnabled)
        }
        if (vibrateEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 150, 250))
            defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
        }
        if (defaults != 0) builder.setDefaults(defaults)

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notification.id.hashCode(), builder.build())
        }
    }

    private fun channelFor(type: NotificationType): String = when (type) {
        NotificationType.NEW_MESSAGE -> CHANNEL_MESSAGES
        NotificationType.BOOKING_REMINDER -> CHANNEL_REMINDERS
        else -> CHANNEL_BOOKINGS
    }

    private fun priorityFor(type: NotificationType): Int = when (type) {
        NotificationType.NEW_MESSAGE,
        NotificationType.NEW_REVIEW,
        NotificationType.SYSTEM -> NotificationCompat.PRIORITY_DEFAULT
        else -> NotificationCompat.PRIORITY_HIGH
    }
}
