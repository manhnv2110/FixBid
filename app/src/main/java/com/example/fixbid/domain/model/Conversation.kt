package com.example.fixbid.domain.model

data class Conversation(
    val id: String,
    val customerId: String,
    val workerId: String,
    val bookingId: String?,            // liên kết với booking nếu có
    val lastMessage: Message?,
    val unreadCount: Int,
    val createdAt: Long,
    val customer: User? = null,
    val worker: User? = null
)