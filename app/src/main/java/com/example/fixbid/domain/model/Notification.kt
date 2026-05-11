package com.example.fixbid.domain.model

data class Notification(
    val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val type: NotificationType,
    val referenceId: String?,          // bookingId / reviewId / ... tùy type
    val isRead: Boolean,
    val createdAt: Long
)

enum class NotificationType {
    BOOKING_REQUEST,       // thợ nhận được yêu cầu đặt lịch
    BOOKING_CONFIRMED,     // khách được thông báo thợ đã confirm
    BOOKING_CANCELLED,
    BID_RECEIVED,          // khách nhận được bid từ thợ
    BID_ACCEPTED,          // thợ được thông báo bid được chọn
    PAYMENT_RECEIVED,
    NEW_MESSAGE,
    NEW_REVIEW,
    SYSTEM
}