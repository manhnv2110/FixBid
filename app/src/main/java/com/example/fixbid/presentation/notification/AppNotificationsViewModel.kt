package com.example.fixbid.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.core.notification.AppNotificationManager
import com.example.fixbid.data.local.UserPreferencesDataStore
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.NotificationRepository
import com.example.fixbid.domain.usecase.shared.ObserveUnreadNotificationCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Session-scoped coordinator for real-time notifications. Hosted at the NavHost
 * root so it stays alive across screen changes. It:
 *  - exposes the live unread count for the notification-bell badge, and
 *  - listens for freshly-inserted notifications and surfaces them as system
 *    notifications (honoring the user's sound / vibration prefs).
 */
@HiltViewModel
class AppNotificationsViewModel @Inject constructor(
    observeUnreadCount: ObserveUnreadNotificationCountUseCase,
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
    private val preferences: UserPreferencesDataStore,
    private val appNotificationManager: AppNotificationManager,
    private val registerFcmTokenUseCase: com.example.fixbid.domain.usecase.shared.RegisterFcmTokenUseCase,
    private val proactiveAssistant: com.example.fixbid.data.repository.ProactiveAssistantCoordinator,
    private val activeChatTracker: com.example.fixbid.core.chat.ActiveChatTracker
) : ViewModel() {

    val unreadCount: StateFlow<Int> = observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _permissionRequested = MutableStateFlow(false)
    val permissionRequested: StateFlow<Boolean> = _permissionRequested.asStateFlow()

    init {
        appNotificationManager.ensureChannels()
        observeIncomingForPush()
        syncPushToken()
        // Kick off the AI proactive assistant — it tails the same realtime
        // notification stream and turns actionable events into chatbot prompts.
        proactiveAssistant.start()
    }

    fun markPermissionRequested() {
        _permissionRequested.value = true
    }

    /**
     * Register this device's FCM token against the signed-in user so the backend
     * can deliver pushes while the app is backgrounded/killed. Safe no-op when
     * push isn't configured or no user is signed in.
     */
    private fun syncPushToken() {
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user != null) {
                    runCatching { registerFcmTokenUseCase() }
                }
            }
        }
    }

    private fun observeIncomingForPush() {
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user == null) return@collectLatest
                notificationRepository.observeNewNotifications(user.id).collect { notification ->
                    val masterEnabled = preferences.notificationsEnabled.first()
                    if (!masterEnabled) return@collect

                    // Skip in-app heads-up for chat messages when the user is
                    // already looking at that exact conversation — the new
                    // bubble lands on screen via Realtime so a notification on
                    // top would be redundant noise.
                    if (
                        notification.type == com.example.fixbid.domain.model.NotificationType.NEW_MESSAGE &&
                        activeChatTracker.isActive(notification.referenceId)
                    ) return@collect

                    val sound = preferences.notificationSoundEnabled.first()
                    val vibrate = preferences.notificationVibrateEnabled.first()
                    appNotificationManager.show(
                        notification = notification,
                        soundEnabled = sound,
                        vibrateEnabled = vibrate
                    )
                }
            }
        }
    }
}
