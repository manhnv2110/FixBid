package com.example.fixbid.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fixbid.domain.model.ChatbotMessage
import com.example.fixbid.domain.model.ChatbotRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device persistence for the AI chatbot conversation.
 *
 * Why not Room? The history is a single ordered append-only list per user — a
 * Room table would add migrations, DAOs and DTOs for what amounts to one row.
 * A DataStore JSON blob is simpler, atomic, and survives process death.
 *
 * Storage rules:
 *  - Keyed by `userId` so multiple accounts on the same device don't collide.
 *  - TTL: messages older than [TTL_MILLIS] are dropped on every load. Keeps
 *    the LLM context window healthy and respects the "delete after 7 days"
 *    behaviour we surface in the UI.
 *  - Hard cap of [MAX_MESSAGES] entries — protects against runaway sessions.
 *  - We never persist an open action card (a confirm button that does
 *    nothing after a kill-restart). [save] sanitises by setting
 *    `actionResolved = true` and dropping the pending action.
 */
private val Context.chatbotDataStore by preferencesDataStore("chatbot_history")

@Singleton
class ChatbotHistoryDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun load(userId: String): List<ChatbotMessage> {
        val raw = context.chatbotDataStore.data.first()[keyFor(userId)] ?: return emptyList()
        return runCatching { json.decodeFromString<StoredHistory>(raw) }.getOrNull()
            ?.messages
            ?.filter { it.createdAt >= System.currentTimeMillis() - TTL_MILLIS }
            ?.map { it.toDomain() }
            ?: emptyList()
    }

    suspend fun save(userId: String, messages: List<ChatbotMessage>) {
        val sanitized = messages
            .takeLast(MAX_MESSAGES)
            .map { msg ->
                if (msg.pendingAction != null && !msg.actionResolved) {
                    msg.copy(actionResolved = true, pendingAction = null)
                } else msg
            }
            .map { StoredMessage.from(it) }

        val payload = json.encodeToString(StoredHistory(version = 1, messages = sanitized))
        context.chatbotDataStore.edit { it[keyFor(userId)] = payload }
    }

    suspend fun clear(userId: String) {
        context.chatbotDataStore.edit { it.remove(keyFor(userId)) }
    }

    private fun keyFor(userId: String) = stringPreferencesKey("history_$userId")

    // ── On-disk format ──────────────────────────────────────────────────────

    @Serializable
    private data class StoredHistory(
        val version: Int,
        val messages: List<StoredMessage> = emptyList()
    )

    @Serializable
    private data class StoredMessage(
        val id: String,
        @SerialName("role") val roleName: String,
        val text: String,
        val createdAt: Long,
        val isError: Boolean = false,
        val navigationRoute: String? = null,
        val actionResolved: Boolean = true
    ) {
        fun toDomain(): ChatbotMessage = ChatbotMessage(
            id = id,
            role = runCatching { ChatbotRole.valueOf(roleName) }.getOrDefault(ChatbotRole.ASSISTANT),
            text = text,
            createdAt = createdAt,
            isError = isError,
            navigationRoute = navigationRoute,
            pendingAction = null,
            actionResolved = actionResolved
        )

        companion object {
            fun from(m: ChatbotMessage) = StoredMessage(
                id = m.id,
                roleName = m.role.name,
                text = m.text,
                createdAt = m.createdAt,
                isError = m.isError,
                navigationRoute = m.navigationRoute,
                actionResolved = m.actionResolved
            )
        }
    }

    private companion object {
        const val MAX_MESSAGES = 200
        const val TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000   // 7 days
    }
}
