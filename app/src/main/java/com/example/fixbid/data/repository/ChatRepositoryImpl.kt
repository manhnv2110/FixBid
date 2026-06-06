package com.example.fixbid.data.repository

import android.util.Log
import com.example.fixbid.data.remote.dto.MessageDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.data.remote.supabase.liveFlow
import com.example.fixbid.domain.model.Conversation
import com.example.fixbid.domain.model.Message
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.ChatRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "ChatRepo"

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
     * Observes messages using the exact pattern from supabase-kt 3.x docs:
     *   1. Create channel
     *   2. Build changeFlow with .onEach { ... }.launchIn(scope)
     *   3. call channel.subscribe()
     *
     * Using callbackFlow gives us a ProducerScope that IS a CoroutineScope,
     * so launchIn(this) works correctly — no deadlock risk.
     *
     * Full Log.d logging at every step so we can diagnose via Logcat.
     */
    override fun observeMessages(conversationId: String): Flow<List<Message>> = callbackFlow {
        Log.d(TAG, "observeMessages: START for conversationId=$conversationId")

        // 1. Fetch and emit initial messages
        val initial = runCatching {
            client.postgrest[Tables.MESSAGES]
                .select(Columns.ALL) {
                    filter { eq("conversation_id", conversationId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<MessageDto>()
                .map { it.toDomain() }
        }.onFailure { Log.e(TAG, "observeMessages: initial fetch failed", it) }
            .getOrDefault(emptyList())

        Log.d(TAG, "observeMessages: emitting ${initial.size} initial messages")
        send(initial)

        // 2. Create a unique channel name to avoid reuse conflicts on re-navigation
        val channelName = "messages_${conversationId}_${System.currentTimeMillis()}"
        Log.d(TAG, "observeMessages: creating channel '$channelName'")
        val channel = client.realtime.channel(channelName)

        // 3. Build the change flow with launchIn — this is the EXACT pattern from
        //    the supabase-kt 3.x documentation. Using launchIn(this) ties the
        //    collection lifecycle to the callbackFlow's producer scope.
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.MESSAGES
        }.onEach { action ->
            Log.d(TAG, "observeMessages: received PostgresAction ${action::class.simpleName}")

            // Client-side filter: only process events for this conversation
            val eventConvId = runCatching {
                when (action) {
                    is PostgresAction.Insert -> {
                        val dto = action.decodeRecord<MessageDto>()
                        Log.d(TAG, "observeMessages: INSERT conversationId=${dto.conversationId} content='${dto.content}'")
                        dto.conversationId
                    }
                    is PostgresAction.Update -> {
                        val dto = action.decodeRecord<MessageDto>()
                        Log.d(TAG, "observeMessages: UPDATE conversationId=${dto.conversationId} isRead=${dto.isRead}")
                        dto.conversationId
                    }
                    is PostgresAction.Delete -> {
                        val dto = action.decodeOldRecord<MessageDto>()
                        Log.d(TAG, "observeMessages: DELETE conversationId=${dto.conversationId}")
                        dto.conversationId
                    }
                    else -> {
                        Log.d(TAG, "observeMessages: SELECT event (ignored)")
                        null
                    }
                }
            }.onFailure { Log.e(TAG, "observeMessages: failed to decode action", it) }
                .getOrNull()

            if (eventConvId == null || eventConvId == conversationId) {
                Log.d(TAG, "observeMessages: filter matched, re-fetching messages")
                val updated = runCatching {
                    client.postgrest[Tables.MESSAGES]
                        .select(Columns.ALL) {
                            filter { eq("conversation_id", conversationId) }
                            order("created_at", Order.ASCENDING)
                        }
                        .decodeList<MessageDto>()
                        .map { it.toDomain() }
                }.onFailure { Log.e(TAG, "observeMessages: re-fetch failed", it) }
                    .getOrNull()

                if (updated != null) {
                    Log.d(TAG, "observeMessages: sending ${updated.size} updated messages")
                    send(updated)
                }
            } else {
                Log.d(TAG, "observeMessages: filter skipped event (eventConvId=$eventConvId, expected=$conversationId)")
            }
        }.launchIn(this) // 'this' = ProducerScope = CoroutineScope of callbackFlow

        // 4. Subscribe AFTER launchIn (collector is already registered by launchIn)
        Log.d(TAG, "observeMessages: subscribing to channel '$channelName'")
        runCatching {
            channel.subscribe(blockUntilSubscribed = true)
        }.onSuccess {
            Log.d(TAG, "observeMessages: channel '$channelName' subscribed successfully ✓")
        }.onFailure {
            Log.e(TAG, "observeMessages: channel subscription FAILED", it)
        }

        // 5. Keep the flow alive until cancelled, then clean up
        awaitClose {
            Log.d(TAG, "observeMessages: closing channel '$channelName'")
            runCatching { kotlinx.coroutines.runBlocking { channel.unsubscribe() } }
                .onSuccess { Log.d(TAG, "observeMessages: channel '$channelName' unsubscribed") }
                .onFailure { Log.e(TAG, "observeMessages: unsubscribe failed", it) }
        }
    }

    override fun observeConversations(userId: String): Flow<List<Conversation>> {
        // A new message in ANY conversation should refresh the list (last message
        // preview + unread badge), for both customer and worker. We watch the
        // messages table globally and re-pull the user's conversations on change.
        val channel = client.realtime.channel("conversations_feed_$userId")
        val changes = channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = Tables.MESSAGES
            }
            .map {
                Log.d(TAG, "observeConversations: message change detected, re-fetching conversations")
                (getConversations(userId) as? Resource.Success)?.data ?: emptyList()
            }
            .onStart {
                Log.d(TAG, "observeConversations: onStart — emitting initial conversations")
                emit((getConversations(userId) as? Resource.Success)?.data ?: emptyList())
            }
        return channel.liveFlow(changes)
    }

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
}