package com.example.fixbid.core.notification

import com.example.fixbid.data.local.UserPreferencesDataStore
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.NotificationType
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.NotificationRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives Firebase Cloud Messaging pushes so booking/chat/payment alerts reach
 * the user even when the app process is killed or backgrounded — the gap that
 * the Supabase-Realtime in-app surface (see [AppNotificationManager]) can't cover.
 *
 * The server is expected to send **data** messages (not `notification` messages)
 * with these keys so we keep full control of the channel + visuals:
 *   - `type`            snake_case [NotificationType] (e.g. "booking_confirmed")
 *   - `title`, `body`   text to display
 *   - `reference_id`    bookingId / reviewId for deep-linking (optional)
 *   - `notification_id` stable id for dedupe (optional)
 *
 * `notification`-style payloads are still handled as a fallback so test sends
 * from the Firebase console also appear.
 *
 * NOTE: This service is only ever instantiated by the system once FCM delivers a
 * message, which requires `google-services.json`. Until Firebase is configured it
 * stays dormant and the app behaves exactly as before.
 */
@AndroidEntryPoint
class FixBidMessagingService : FirebaseMessagingService() {

    @Inject lateinit var appNotificationManager: AppNotificationManager
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var preferences: UserPreferencesDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called when FCM issues a fresh registration token (first launch, app data
     * cleared, token rotation). Persist it locally and, if a session exists,
     * upsert it to the backend so the server can target this device.
     */
    override fun onNewToken(token: String) {
        scope.launch {
            runCatching {
                preferences.saveFcmToken(token)
                val user = authRepository.getCurrentUser() ?: return@launch
                notificationRepository.saveFcmToken(user.id, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "FixBid"
        val body = data["body"] ?: message.notification?.body.orEmpty()
        val type = NotificationType.fromRaw(data["type"] ?: NotificationType.SYSTEM.dbValue)
        val referenceId = data["reference_id"]
        val notificationId = data["notification_id"]
            ?: System.currentTimeMillis().toString()

        val notification = Notification(
            id = notificationId,
            userId = "",
            title = title,
            body = body,
            type = type,
            referenceId = referenceId,
            isRead = false,
            createdAt = System.currentTimeMillis()
        )

        scope.launch {
            // Respect the user's master toggle; sound/vibration mirror the
            // in-app surface so behaviour is identical whether the push arrives
            // via Realtime (foreground) or FCM (background).
            val masterEnabled = runCatching { preferences.notificationsEnabled.first() }
                .getOrDefault(true)
            if (!masterEnabled) return@launch
            val sound = runCatching { preferences.notificationSoundEnabled.first() }
                .getOrDefault(true)
            val vibrate = runCatching { preferences.notificationVibrateEnabled.first() }
                .getOrDefault(true)

            appNotificationManager.ensureChannels()
            appNotificationManager.show(
                notification = notification,
                soundEnabled = sound,
                vibrateEnabled = vibrate
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
