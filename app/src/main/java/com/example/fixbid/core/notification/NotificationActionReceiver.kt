package com.example.fixbid.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.fixbid.domain.repository.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles notification action buttons that don't open the app — currently
 * "Mark as read". Receives the broadcast posted by [AppNotificationManager],
 * dismisses the system notification, and updates the backing row in the
 * notifications table so the unread badge stays in sync.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationRepository: NotificationRepository

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID) ?: return
        val systemId = intent.getIntExtra(EXTRA_SYSTEM_ID, notificationId.hashCode())

        // Dismiss the system notification immediately so the tap feels instant.
        NotificationManagerCompat.from(context).cancel(systemId)

        // Persist the read flag. goAsync() keeps the receiver alive long enough
        // for the network call without blocking the main thread.
        val pendingResult = goAsync()
        scope.launch {
            runCatching { notificationRepository.markAsRead(notificationId) }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_MARK_READ = "com.example.fixbid.action.MARK_NOTIFICATION_READ"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_SYSTEM_ID = "extra_system_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
