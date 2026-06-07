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
import androidx.core.graphics.drawable.IconCompat
import com.example.fixbid.MainActivity
import com.example.fixbid.R
import com.example.fixbid.core.utils.NotificationIconMapper
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.NotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the platform [NotificationManager] for FixBid. Declares the channels,
 * maps a domain [Notification] to a richly-styled system notification, and
 * respects the user's sound / vibration preferences.
 *
 * Visual design choices:
 *  - Mono wrench glyph as the small icon (status bar) with a per-type accent
 *    colour so users learn to recognise booking vs. message vs. payment from
 *    the colour alone.
 *  - Brand launcher mark as the large icon (the round image to the right of
 *    the body) so notifications "look like FixBid" in the shade.
 *  - Emoji prefix on the title line as a fast non-text cue.
 *  - Vietnamese type label on the second line via [NotificationCompat.Builder.setSubText].
 *  - BigTextStyle so long bodies expand on long-press.
 *  - Group key + summary so multiple booking updates collapse into one card
 *    instead of stacking five identical rows.
 *  - Action buttons ("Mở", "Đánh dấu đã đọc") for one-tap interaction.
 */
@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_BOOKINGS = "fixbid_bookings"
        const val CHANNEL_MESSAGES = "fixbid_messages"
        const val CHANNEL_REMINDERS = "fixbid_reminders"

        const val DEEPLINK_NOTIFICATION_ID = "notification_id"
        const val DEEPLINK_REFERENCE_ID = "reference_id"
        const val DEEPLINK_TYPE = "notification_type"

        private const val GROUP_BOOKINGS = "fixbid_group_bookings"
        private const val GROUP_MESSAGES = "fixbid_group_messages"
        private const val GROUP_REMINDERS = "fixbid_group_reminders"

        // Stable summary ids — using a fixed id per group lets us update the
        // summary in place instead of stacking new ones.
        private const val SUMMARY_ID_BOOKINGS = 0x10000001
        private const val SUMMARY_ID_MESSAGES = 0x10000002
        private const val SUMMARY_ID_REMINDERS = 0x10000003
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
            vibrationPattern = longArrayOf(0, 220, 120, 220)
            enableLights(true)
        }

        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            "Tin nhắn & cập nhật",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Tin nhắn mới và các cập nhật chung"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 80, 120)
        }

        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            "Nhắc lịch hẹn",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Nhắc nhở trước các buổi hẹn dịch vụ"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            enableLights(true)
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
     * Post a system notification for [notification], honouring the user's
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
        val groupKey = groupFor(notification.type)
        val accent = NotificationVisuals.accentColor(notification.type)
        val systemId = notification.id.hashCode()

        val contentIntent = buildContentIntent(notification)
        val markReadIntent = buildMarkReadIntent(notification.id, systemId)

        val largeIcon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val emojiTitle = "${emojiFor(notification.type)} ${notification.title}".trim()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setLargeIcon(largeIcon.toIcon(context))
            .setContentTitle(emojiTitle)
            .setContentText(notification.body)
            .setSubText(NotificationIconMapper.getLabel(notification.type))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(emojiTitle)
                    .bigText(notification.body)
                    .setSummaryText(NotificationIconMapper.getLabel(notification.type))
            )
            .setColor(accent)
            .setColorized(false) // Colorize is reserved for foreground-service style ongoing notifs.
            .setAutoCancel(true)
            .setCategory(categoryFor(notification.type))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(priorityFor(notification.type))
            .setShowWhen(true)
            .setWhen(notification.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setContentIntent(contentIntent)
            .setGroup(groupKey)
            .addAction(
                R.drawable.ic_stat_notification,
                "Mở",
                contentIntent
            )
            .addAction(
                R.drawable.ic_stat_notification,
                "Đánh dấu đã đọc",
                markReadIntent
            )

        applySoundAndVibration(builder, soundEnabled, vibrateEnabled)

        runCatching {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(systemId, builder.build())
            // Post / refresh the group summary so multiple notifications of the
            // same kind collapse into a tidy "FixBid · 3 thông báo" row.
            postSummary(nm, channelId, groupKey, accent)
        }
    }

    /** Cancel a single posted notification (used by the action receiver). */
    fun cancel(systemId: Int) {
        NotificationManagerCompat.from(context).cancel(systemId)
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun buildContentIntent(notification: Notification): PendingIntent =
        PendingIntent.getActivity(
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

    private fun buildMarkReadIntent(notificationId: String, systemId: Int): PendingIntent {
        val intent = Intent(NotificationActionReceiver.ACTION_MARK_READ).apply {
            setPackage(context.packageName)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_SYSTEM_ID, systemId)
        }
        // Use the notification's hash as the request code so each item gets its
        // own PendingIntent and updates don't clobber other notifications.
        return PendingIntent.getBroadcast(
            context,
            notificationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun applySoundAndVibration(
        builder: NotificationCompat.Builder,
        soundEnabled: Boolean,
        vibrateEnabled: Boolean
    ) {
        // On O+ the channel governs sound/vibration; on pre-O we still set them
        // explicitly. setSilent() opts out cleanly when both are off.
        if (!soundEnabled && !vibrateEnabled) {
            builder.setSilent(true)
            return
        }
        var defaults = 0
        if (soundEnabled) {
            val sound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(sound)
            defaults = defaults or NotificationCompat.DEFAULT_SOUND
        }
        if (vibrateEnabled) {
            builder.setVibrate(longArrayOf(0, 220, 120, 220))
            defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
        }
        if (defaults != 0) builder.setDefaults(defaults)
    }

    /** Posts (or refreshes) the silent summary for [groupKey]. */
    private fun postSummary(
        nm: NotificationManagerCompat,
        channelId: String,
        groupKey: String,
        accent: Int
    ) {
        val summaryId = when (groupKey) {
            GROUP_BOOKINGS -> SUMMARY_ID_BOOKINGS
            GROUP_MESSAGES -> SUMMARY_ID_MESSAGES
            GROUP_REMINDERS -> SUMMARY_ID_REMINDERS
            else -> return
        }
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(accent)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("FixBid"))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { nm.notify(summaryId, summary) }
    }

    private fun channelFor(type: NotificationType): String = when (type) {
        NotificationType.NEW_MESSAGE -> CHANNEL_MESSAGES
        NotificationType.BOOKING_REMINDER -> CHANNEL_REMINDERS
        else -> CHANNEL_BOOKINGS
    }

    private fun groupFor(type: NotificationType): String = when (type) {
        NotificationType.NEW_MESSAGE -> GROUP_MESSAGES
        NotificationType.BOOKING_REMINDER -> GROUP_REMINDERS
        else -> GROUP_BOOKINGS
    }

    private fun priorityFor(type: NotificationType): Int = when (type) {
        NotificationType.NEW_MESSAGE,
        NotificationType.NEW_REVIEW,
        NotificationType.SYSTEM -> NotificationCompat.PRIORITY_DEFAULT
        else -> NotificationCompat.PRIORITY_HIGH
    }

    private fun categoryFor(type: NotificationType): String = when (type) {
        NotificationType.NEW_MESSAGE -> NotificationCompat.CATEGORY_MESSAGE
        NotificationType.BOOKING_REMINDER -> NotificationCompat.CATEGORY_REMINDER
        NotificationType.PAYMENT_RECEIVED -> NotificationCompat.CATEGORY_STATUS
        NotificationType.WORKER_ON_THE_WAY,
        NotificationType.JOB_STARTED,
        NotificationType.JOB_COMPLETED -> NotificationCompat.CATEGORY_PROGRESS
        else -> NotificationCompat.CATEGORY_STATUS
    }

    /** Quick non-text cue prefixed onto the title; matches the colour scheme. */
    private fun emojiFor(type: NotificationType): String = when (type) {
        NotificationType.BOOKING_REQUEST -> "🛎️"
        NotificationType.BOOKING_CONFIRMED -> "✅"
        NotificationType.BOOKING_CANCELLED -> "❌"
        NotificationType.BOOKING_REMINDER -> "⏰"
        NotificationType.BOOKING_QUOTED -> "💵"
        NotificationType.BOOKING_QUOTE_ACCEPTED -> "🤝"
        NotificationType.BOOKING_QUOTE_REJECTED -> "🔁"
        NotificationType.BID_RECEIVED -> "💬"
        NotificationType.BID_ACCEPTED -> "🎉"
        NotificationType.WORKER_ON_THE_WAY -> "🚗"
        NotificationType.JOB_STARTED -> "🛠️"
        NotificationType.JOB_COMPLETED -> "🏁"
        NotificationType.PAYMENT_RECEIVED -> "💰"
        NotificationType.NEW_MESSAGE -> "💬"
        NotificationType.NEW_REVIEW -> "⭐"
        NotificationType.SYSTEM -> "🔔"
    }
}
