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
    BOOKING_QUOTED,        // khách: thợ đã gửi báo giá cho đơn đặt trực tiếp
    BOOKING_QUOTE_ACCEPTED,// thợ: khách đã chấp nhận báo giá
    BOOKING_QUOTE_REJECTED,// thợ: khách từ chối báo giá / yêu cầu báo lại
    BOOKING_CONFIRMED,     // khách được thông báo thợ đã confirm
    BOOKING_CANCELLED,
    BOOKING_REMINDER,      // nhắc lịch hẹn sắp tới (cleaning schedule reminder)
    BID_RECEIVED,          // khách nhận được bid từ thợ
    BID_ACCEPTED,          // thợ được thông báo bid được chọn
    WORKER_ON_THE_WAY,     // khách: thợ đang trên đường tới
    JOB_STARTED,           // khách: thợ đã bắt đầu công việc
    JOB_COMPLETED,         // khách: thợ báo đã hoàn thành, chờ xác nhận
    PAYMENT_RECEIVED,
    NEW_MESSAGE,
    NEW_REVIEW,
    SYSTEM;

    companion object {
        /** Parse an toàn từ chuỗi (snake_case từ DB) — fallback về SYSTEM. */
        fun fromRaw(raw: String): NotificationType =
            runCatching { valueOf(raw.uppercase()) }.getOrDefault(SYSTEM)
    }

    /** Giá trị lưu xuống cột enum `notification_type` của Postgres. */
    val dbValue: String get() = name.lowercase()
}
