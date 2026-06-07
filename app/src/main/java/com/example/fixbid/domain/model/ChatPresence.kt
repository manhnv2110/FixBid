package com.example.fixbid.domain.model

/**
 * Live presence info for the counterparty in a one-to-one conversation.
 *
 * - [online]: the other side is currently subscribed to this conversation's
 *   Supabase Realtime Presence channel — i.e. they have the chat thread open
 *   right now.
 * - [isTyping]: the other side is composing a message (debounced ~3 s).
 * - [lastSeenAt]: the most recent moment we observed them online; used for
 *   the "Hoạt động N phút trước" subtitle when [online] is false.
 *
 * Rendered by `ChatScreen` as the green dot + status subtitle in the header.
 */
data class ChatPresence(
    val online: Boolean = false,
    val isTyping: Boolean = false,
    val lastSeenAt: Long? = null
)
