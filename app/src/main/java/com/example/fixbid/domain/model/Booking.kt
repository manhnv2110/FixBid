package com.example.fixbid.domain.model

data class Booking(
    val id: String,
    val customerId: String,
    val workerId: String,
    val category: ServiceCategory,
    val description: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val scheduledAt: Long,                             // timestamp khách muốn
    val estimatedDurationHours: Double,
    val status: BookingStatus,
    val type: BookingType,
    val agreedPrice: Double?,                          // giá đã thống nhất (sau đấu thầu hoặc confirm)
    val customerNote: String?,
    val workerNote: String?,
    val cancelReason: String? = null,                   // lý do huỷ — viết khi worker từ chối / customer huỷ
    val descriptionImages: List<String>? = emptyList(), // URL ảnh mô tả công việc từ khách
    val completionNote: String? = null,                // ghi chú hoàn thành từ thợ
    val completionImages: List<String>? = emptyList(),  // URL ảnh thực tế sau khi hoàn thành
    // ── Direct-booking quote flow ──────────────────────────────────────────
    // Khi khách đặt trực tiếp một thợ, thợ phải đề xuất giá trước khi khách
    // thanh toán. Các trường này được set trong giai đoạn QUOTED, sau đó được
    // copy sang `agreedPrice` khi khách chấp nhận giá.
    val quotedPrice: Double? = null,                    // giá thợ đề xuất cho đơn direct
    val quoteMessage: String? = null,                   // ghi chú/giải thích đi kèm báo giá
    val quotedAt: Long? = null,                         // thời điểm thợ gửi báo giá
    val quoteEstimatedDurationHours: Double? = null,    // thợ ước lượng cần bao lâu
    val createdAt: Long,
    val updatedAt: Long,
    // Relations (được load kèm theo context)
    val customer: User? = null,
    val worker: User? = null,
    val payment: Payment? = null
)

enum class BookingStatus {
    PENDING,             // khách vừa tạo, chờ thợ
    BIDDING,             // đang trong giai đoạn đấu thầu
    QUOTED,              // thợ đã báo giá cho đơn direct, chờ khách duyệt
    AWAITING_PAYMENT,    // khách đã chọn thợ, chờ thanh toán
    CONFIRMED,           // đã thanh toán, thợ có thể bắt đầu
    IN_PROGRESS,         // đang làm
    PENDING_COMPLETION,  // thợ báo xong, chờ khách xác nhận hoàn thành
    COMPLETED,           // hoàn thành, chờ review
    CANCELLED,           // đã hủy
    DISPUTED             // có tranh chấp
}

enum class BookingType {
    DIRECT,   // khách đặt thẳng một thợ, thợ confirm
    BIDDING   // khách post yêu cầu, nhiều thợ đặt giá
}