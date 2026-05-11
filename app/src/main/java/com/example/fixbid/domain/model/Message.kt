package com.example.fixbid.domain.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val type: MessageType,
    val imageUrl: String?,             // nếu gửi ảnh
    val isRead: Boolean,
    val createdAt: Long
)

enum class MessageType {
    TEXT,
    IMAGE,
    BOOKING_CARD,                      // tin nhắn hệ thống: "Booking #123 đã được xác nhận"
    SYSTEM
}