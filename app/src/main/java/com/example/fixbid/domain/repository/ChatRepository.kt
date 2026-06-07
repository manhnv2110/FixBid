package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.ChatPresence
import com.example.fixbid.domain.model.Conversation
import com.example.fixbid.domain.model.Message
import com.example.fixbid.domain.model.Resource
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun getOrCreateConversation(
        customerId: String,
        workerId: String,
        bookingId: String? = null
    ): Resource<Conversation>

    suspend fun getConversations(userId: String): Resource<List<Conversation>>
    suspend fun getMessages(conversationId: String): Resource<List<Message>>
    suspend fun sendMessage(message: Message): Resource<Message>
    suspend fun markAsRead(conversationId: String, userId: String): Resource<Unit>

    /**
     * True realtime stream of messages in [conversationId]. Emits the full
     * message list on first collection, then re-emits after every INSERT /
     * UPDATE / DELETE event for that conversation. Cancellation
     * unsubscribes the underlying WebSocket channel.
     */
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /**
     * Live conversation list for [userId]. Refreshes when any of the user's
     * conversations receives a new message or read-receipt update.
     */
    fun observeConversations(userId: String): Flow<List<Conversation>>

    /**
     * Subscribe to the Supabase Realtime *Presence + Broadcast* channel for
     * [conversationId] using [currentUserId] as the local presence key. Emits
     * the live [ChatPresence] of the counterparty (online flag, typing flag,
     * lastSeen). Collecting this flow registers our own presence — when the
     * collector cancels we untrack and the other side sees us go offline.
     *
     * Use [sendTypingIndicator] to broadcast our typing state on the same
     * channel.
     */
    fun observePresence(conversationId: String, currentUserId: String): Flow<ChatPresence>

    /**
     * Broadcast a typing-state change on the conversation's Realtime channel.
     * The receiver flips `isTyping` for ~3 s before automatically clearing.
     * Safe to call rapidly — internally rate-limited by the backend.
     */
    suspend fun sendTypingIndicator(conversationId: String, currentUserId: String, isTyping: Boolean)

    /**
     * Upload an image attachment for [conversationId] to Storage and return
     * the public URL. The caller then sends a [com.example.fixbid.domain.model.Message]
     * with `type = IMAGE` and `imageUrl = <returned url>`.
     */
    suspend fun uploadChatImage(
        conversationId: String,
        imageBytes: ByteArray,
        fileName: String
    ): Resource<String>
}
