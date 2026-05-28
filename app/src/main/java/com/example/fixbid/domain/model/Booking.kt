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
    val descriptionImages: List<String>? = emptyList(), // URL ảnh mô tả công việc từ khách
    val completionNote: String? = null,                // ghi chú hoàn thành từ thợ
    val completionImages: List<String>? = emptyList(),  // URL ảnh thực tế sau khi hoàn thành
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