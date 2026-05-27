package com.example.fixbid.domain.repository

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
    fun observeMessages(conversationId: String): Flow<List<Message>>
    fun observeConversations(userId: String): Flow<List<Conversation>>
}