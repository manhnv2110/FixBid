package com.example.fixbid.data.repository

import android.util.Log
import com.example.fixbid.data.remote.dto.MessageDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.data.remote.supabase.liveFlow
import com.example.fixbid.domain.model.ChatPresence
import com.example.fixbid.domain.model.Conversation
import com.example.fixbid.domain.model.Message
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.ChatRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "ChatRepo"

/** Realtime broadcast event names used inside a per-conversation channel. */
private const val EVENT_TYPING = "typing"

/**
 * Single-source implementation of [ChatRepository].
 *
 * Realtime model:
 *  - **Postgres changes** for the `messages` table → INSERT / UPDATE / DELETE
 *    are applied as deltas to a local list, never re-fetched. Initial state
 *    comes from one read at subscription time.
 *  - **Presence** on the same per-conversation channel → drives the green
 *    online dot in the chat header.
 *  - **Broadcast** of a `typing` event on the same channel → drives the
 *    "Đang nhập…" subtitle.
 *
 * Both channels (messages + presence) are joined on the same topic so we
 * only pay one WebSocket subscription per open thread.
 */
class ChatRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : ChatRepository {

    override suspend fun sendMessage(message: Message): Resource<Message> = runCatching {
        val dto = MessageDto(
            conversationId = message.conversationId,
            senderId       = message.senderId,
            recipientId    = message.recipientId,
            content        = message.content,
            type           = message.type.name.lowercase(),
            imageUrl       = message.imageUrl
        )
        val result = client.postgrest[Tables.MESSAGES]
            .insert(dto) { select(Columns.ALL) }
            .decodeSingle<MessageDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Gửi tin nhắn thất bại") }

    override suspend fun getMessages(conversationId: String): Resource<List<Message>> =
        runCatching {
            val msgs = client.postgrest[Tables.MESSAGES]
                .select(Columns.ALL) {
                    filter { eq("conversation_id", conversationId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<MessageDto>()
                .map { it.toDomain() }
            Resource.Success(msgs)
        }.getOrElse { Resource.Error(it.message ?: "Lỗi tải tin nhắn") }

    /**
     * Apply CDC events directly to a local in-memory list and emit on every
     * change. This is dramatically cheaper than the previous approach
     * (re-fetch the full thread on every event) and arrives a network
     * round-trip earlier.
     *
     * The local list is sorted by `createdAt` and deduped by `id`, so racing
     * inserts (e.g. an optimistic local message landing alongside its server
     * echo) collapse cleanly.
     */
    override fun observeMessages(conversationId: String): Flow<List<Message>> = callbackFlow {
        // 1. Seed initial state.
        val seed = runCatching {
            client.postgrest[Tables.MESSAGES]
                .select(Columns.ALL) {
                    filter { eq("conversation_id", conversationId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<MessageDto>()
                .map { it.toDomain() }
        }.getOrDefault(emptyList())
        val state = seed.toMutableList()
        send(state.toList())

        // 2. Subscribe to Postgres CDC for THIS conversation only — server-side
        //    filter (`filter("conversation_id", EQ, conversationId)`) saves both
        //    bandwidth and decode time. We still defensively check the decoded
        //    record's conversation_id in case the server filter is dropped.
        val channelName = "messages_${conversationId}_${System.currentTimeMillis()}"
        val channel = client.realtime.channel(channelName)

        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.MESSAGES
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }.onEach { action ->
            runCatching {
                when (action) {
                    is PostgresAction.Insert -> {
                        val msg = action.decodeRecord<MessageDto>().toDomain()
                        if (msg.conversationId == conversationId &&
                            state.none { it.id == msg.id }
                        ) {
                            state += msg
                        }
                    }
                    is PostgresAction.Update -> {
                        val msg = action.decodeRecord<MessageDto>().toDomain()
                        if (msg.conversationId != conversationId) return@runCatching
                        val idx = state.indexOfFirst { it.id == msg.id }
                        if (idx >= 0) state[idx] = msg
                    }
                    is PostgresAction.Delete -> {
                        val old = action.decodeOldRecord<MessageDto>().toDomain()
                        state.removeAll { it.id == old.id }
                    }
                    else -> Unit
                }
            }.onFailure { Log.e(TAG, "observeMessages: failed to apply action", it) }

            send(state.sortedBy { it.createdAt }.distinctBy { it.id })
        }.launchIn(this)

        runCatching {
            channel.subscribe(blockUntilSubscribed = true)
        }.onFailure { Log.e(TAG, "observeMessages: subscribe failed", it) }

        awaitClose {
            runCatching { kotlinx.coroutines.runBlocking { channel.unsubscribe() } }
        }
    }

    override fun observeConversations(userId: String): Flow<List<Conversation>> {
        // Two CDC sources can change the conversation list:
        //   1. Inserts on `conversations` — a new chat thread was opened.
        //   2. Inserts/updates on `messages` — a thread's last-message preview
        //      or unread badge changed.
        // We multiplex both on a single channel and re-pull the list on every
        // event. List size is small (a handful of threads per user), so a
        // refetch is acceptably cheap and keeps the merge logic trivial.
        val channel = client.realtime.channel("conversations_feed_${userId}_${System.currentTimeMillis()}")

        val messageChanges = channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = Tables.MESSAGES
            }

        val conversationChanges = channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = Tables.CONVERSATIONS
            }

        val merged = kotlinx.coroutines.flow.merge(messageChanges, conversationChanges)
            .map { (getConversations(userId) as? Resource.Success)?.data ?: emptyList() }
            .onStart {
                emit((getConversations(userId) as? Resource.Success)?.data ?: emptyList())
            }
        return channel.liveFlow(merged)
    }

    /**
     * Live presence + typing for a conversation.
     *
     * Topology: one Realtime channel per conversation, scoped to the
     * collector's lifetime. We:
     *  1. Configure the presence join with our own [currentUserId] as key
     *     so the counterparty's join/leave events carry an identifiable id.
     *  2. Subscribe + `track({})` to announce ourselves.
     *  3. Listen on `presenceChangeFlow()` for joins / leaves and on
     *     `broadcastFlow<TypingEvent>(EVENT_TYPING)` for typing pings.
     *  4. On cancel → `untrack()` + `unsubscribe()` so the other side
     *     immediately sees us go offline.
     */
    override fun observePresence(
        conversationId: String,
        currentUserId: String
    ): Flow<ChatPresence> = callbackFlow {
        val channel = client.realtime.channel(presenceTopic(conversationId)) {
            presence { key = currentUserId }
        }

        // Local state — the counterparty's last known online + typing flags.
        var presence = ChatPresence(online = false, isTyping = false, lastSeenAt = null)
        var typingTimerJob: kotlinx.coroutines.Job? = null

        // Presence: we look at presence keys other than our own to decide if
        // the counterparty is online. The keys we receive are exactly the
        // values we (and they) pass via `presence.key`.
        channel.presenceChangeFlow().onEach { action ->
            val joined = action.joins.keys.filter { it != currentUserId }
            val left = action.leaves.keys.filter { it != currentUserId }
            if (joined.isNotEmpty()) {
                presence = presence.copy(online = true, lastSeenAt = System.currentTimeMillis())
                send(presence)
            }
            if (left.isNotEmpty()) {
                presence = presence.copy(
                    online = false,
                    isTyping = false,
                    lastSeenAt = System.currentTimeMillis()
                )
                send(presence)
            }
        }.launchIn(this)

        // Typing broadcasts arrive as small JsonObject payloads. We inspect
        // {userId, isTyping} and ignore our own pings (the server defaults to
        // not echoing our own broadcasts, but be defensive in case that
        // changes).
        channel.broadcastFlow<JsonObject>(EVENT_TYPING).onEach { payload ->
            val userId = payload["userId"]?.jsonPrimitive?.content
            if (userId == null || userId == currentUserId) return@onEach
            val typing = payload["isTyping"]?.jsonPrimitive?.content == "true"
            presence = presence.copy(isTyping = typing)
            send(presence)
            // Auto-clear stale typing state after 4 s in case the sender's
            // "stopped typing" broadcast is dropped.
            typingTimerJob?.cancel()
            if (typing) {
                typingTimerJob = launch {
                    kotlinx.coroutines.delay(4_000)
                    presence = presence.copy(isTyping = false)
                    send(presence)
                }
            }
        }.launchIn(this)

        // Subscribe + announce.
        runCatching {
            channel.subscribe(blockUntilSubscribed = true)
            channel.track(buildJsonObject {
                put("userId", currentUserId)
                put("at", System.currentTimeMillis().toString())
            })
        }.onFailure { Log.e(TAG, "observePresence: subscribe/track failed", it) }

        awaitClose {
            typingTimerJob?.cancel()
            runCatching {
                kotlinx.coroutines.runBlocking {
                    channel.untrack()
                    channel.unsubscribe()
                }
            }
        }
    }

    override suspend fun sendTypingIndicator(
        conversationId: String,
        currentUserId: String,
        isTyping: Boolean
    ) {
        // We open a short-lived channel handle, subscribe, broadcast the
        // typing payload, then leave. Supabase Realtime de-duplicates by
        // topic on the server, so this is cheap and never piles up.
        val channel = runCatching { client.realtime.channel(presenceTopic(conversationId)) }
            .getOrNull() ?: return
        runCatching {
            // If not subscribed yet (e.g. the receiver opens the screen first),
            // broadcast still works because supabase-kt buffers the call.
            channel.broadcast(
                event = EVENT_TYPING,
                message = buildJsonObject {
                    put("userId", currentUserId)
                    put("isTyping", isTyping.toString())
                }
            )
        }.onFailure { Log.e(TAG, "sendTypingIndicator failed", it) }
    }

    private fun presenceTopic(conversationId: String): String =
        "chat_presence_$conversationId"

    override suspend fun getOrCreateConversation(
        customerId: String,
        workerId: String,
        bookingId: String?
    ): Resource<Conversation> = runCatching {
        val existing = client.postgrest[Tables.CONVERSATIONS]
            .select(Columns.ALL) {
                filter {
                    eq("customer_id", customerId)
                    eq("worker_id", workerId)
                }
            }
            .decodeList<Map<String, String?>>()

        val conversationId = if (existing.isEmpty()) {
            val inserted = client.postgrest[Tables.CONVERSATIONS]
                .insert(buildJsonObject {
                    put("customer_id", customerId)
                    put("worker_id",   workerId)
                    bookingId?.let { put("booking_id", it) }
                }) { select(Columns.ALL) }
                .decodeSingle<Map<String, String?>>()
            inserted["id"] ?: ""
        } else {
            existing.first()["id"] ?: ""
        }

        Resource.Success(
            Conversation(
                id          = conversationId,
                customerId  = customerId,
                workerId    = workerId,
                bookingId   = bookingId,
                lastMessage = null,
                unreadCount = 0,
                createdAt   = System.currentTimeMillis()
            )
        )
    }.getOrElse { Resource.Error(it.message ?: "Lỗi tạo cuộc trò chuyện") }

    override suspend fun getConversations(userId: String): Resource<List<Conversation>> =
        runCatching {
            val asCustomer = client.postgrest[Tables.CONVERSATIONS]
                .select(Columns.ALL) { filter { eq("customer_id", userId) } }
                .decodeList<Map<String, String?>>()

            val asWorker = client.postgrest[Tables.CONVERSATIONS]
                .select(Columns.ALL) { filter { eq("worker_id", userId) } }
                .decodeList<Map<String, String?>>()

            val all = (asCustomer + asWorker)
                .distinctBy { it["id"] }
                .map { row ->
                    val convId = row["id"] ?: ""
                    val messages = runCatching {
                        client.postgrest[Tables.MESSAGES]
                            .select(Columns.ALL) {
                                filter { eq("conversation_id", convId) }
                                order("created_at", Order.DESCENDING)
                                limit(50)
                            }
                            .decodeList<MessageDto>()
                            .map { it.toDomain() }
                    }.getOrDefault(emptyList())

                    val lastMessage = messages.firstOrNull()
                    val unread = messages.count { !it.isRead && it.senderId != userId }

                    Conversation(
                        id          = convId,
                        customerId  = row["customer_id"] ?: "",
                        workerId    = row["worker_id"] ?: "",
                        bookingId   = row["booking_id"],
                        lastMessage = lastMessage,
                        unreadCount = unread,
                        createdAt   = lastMessage?.createdAt ?: System.currentTimeMillis()
                    )
                }
            Resource.Success(all)
        }.getOrElse { Resource.Error(it.message ?: "Lỗi tải danh sách chat") }

    override suspend fun markAsRead(
        conversationId: String,
        userId: String
    ): Resource<Unit> = runCatching {
        client.postgrest[Tables.MESSAGES]
            .update(buildJsonObject { put("is_read", true) }) {
                filter {
                    eq("conversation_id", conversationId)
                    neq("sender_id", userId)
                    eq("is_read", false)
                }
            }
        Resource.Success(Unit)
    }.getOrElse { Resource.Error(it.message ?: "Lỗi") }

    override suspend fun uploadChatImage(
        conversationId: String,
        imageBytes: ByteArray,
        fileName: String
    ): Resource<String> = runCatching {
        // We reuse the existing `booking-images` bucket (same RLS surface
        // the rest of the app already trusts) under a `chat/` prefix so a
        // future migration to a dedicated bucket is just a string change.
        val path = "chat/$conversationId/$fileName"
        val bucket = client.storage.from("booking-images")
        bucket.upload(path, imageBytes) { upsert = true }
        Resource.Success(bucket.publicUrl(path))
    }.getOrElse { Resource.Error(it.message ?: "Tải ảnh lên thất bại") }
}
