package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.MessageDto
import com.example.fixbid.data.remote.supabase.Tables
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
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import io.github.jan.supabase.postgrest.query.filter.FilterOperator

class ChatRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : ChatRepository {

    override suspend fun sendMessage(message: Message): Resource<Message> = runCatching {
        val dto = MessageDto(
            conversationId = message.conversationId,
            senderId       = message.senderId,
            content        = message.content,
            type           = message.type.name.lowercase(),
            imageUrl       = message.imageUrl
        )
        val result = client.postgrest[Tables.MESSAGES]
            .insert(dto) { select(Columns.ALL) }
            .decodeSingle<MessageDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Gửi tin nhắn thất bại") }

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        val channel = client.realtime.channel("messages_$conversationId")
        return channel
            .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = Tables.MESSAGES
                filter("conversation_id", FilterOperator.EQ, conversationId)
            }
            .map {
                client.postgrest[Tables.MESSAGES]
                    .select(Columns.ALL) {
                        filter { eq("conversation_id", conversationId) }
                        order("created_at", Order.ASCENDING)
                    }
                    .decodeList<MessageDto>()
                    .map { it.toDomain() }
            }
    }

    override fun observeConversations(userId: String): Flow<List<Conversation>> {
        val channel = client.realtime.channel("conversations_$userId")
        return channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = Tables.CONVERSATIONS
                filter("customer_id", FilterOperator.EQ, userId)
            }
            .map {
                (getConversations(userId) as? Resource.Success)?.data ?: emptyList()
            }
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
                    Conversation(
                        id          = row["id"] ?: "",
                        customerId  = row["customer_id"] ?: "",
                        workerId    = row["worker_id"] ?: "",
                        bookingId   = row["booking_id"],
                        lastMessage = null,
                        unreadCount = 0,
                        createdAt   = System.currentTimeMillis()
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