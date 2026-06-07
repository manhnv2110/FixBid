package com.example.fixbid.core.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide tracker for the conversation the user is currently looking at.
 *
 * Used by [com.example.fixbid.presentation.notification.AppNotificationsViewModel]
 * to suppress NEW_MESSAGE in-app push notifications for the chat thread the
 * user is already actively viewing — the message bubble will land on screen
 * via Realtime, so a heads-up notification on top of it would be redundant.
 *
 * Lifecycle is owned by [com.example.fixbid.presentation.customer.chat.ChatViewModel]:
 *  - `onCleared` (or screen exit) → clear the active id.
 *  - `init` → set it to the currently-viewed conversationId.
 */
@Singleton
class ActiveChatTracker @Inject constructor() {

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    fun enter(conversationId: String) {
        _activeConversationId.value = conversationId.takeIf { it.isNotBlank() }
    }

    fun leave(conversationId: String) {
        if (_activeConversationId.value == conversationId) {
            _activeConversationId.value = null
        }
    }

    /** True when the recipient is already on screen and the notif should be skipped. */
    fun isActive(conversationId: String?): Boolean {
        if (conversationId.isNullOrBlank()) return false
        return _activeConversationId.value == conversationId
    }
}
