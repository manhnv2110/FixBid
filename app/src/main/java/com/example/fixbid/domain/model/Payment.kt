package com.example.fixbid.domain.model

data class Payment(
    val id: String,
    val bookingId: String,
    val customerId: String,
    val workerId: String,
    val amount: Double,
    val platformFee: Double,           // phí app (10%)
    val workerReceives: Double,        // amount - platformFee
    val method: PaymentMethod,
    val status: PaymentStatus,
    val transactionId: String?,        // mã từ cổng thanh toán
    val paidAt: Long?,
    val releasedAt: Long?,             // thời điểm tiền được release cho thợ
    val escrowStatus: EscrowStatus,    // trạng thái giữ tiền
    val createdAt: Long
)

enum class PaymentMethod {
    CASH,           // tiền mặt (xác nhận thủ công)
    MOMO,
    VNPAY,
    BANK_TRANSFER
}

enum class PaymentStatus {
    PENDING,        // chờ thanh toán
    PROCESSING,     // đang xử lý (redirect sang VNPay)
    ESCROW,         // tiền đã được giữ (thanh toán thành công, chờ hoàn thành job)
    COMPLETED,      // hoàn tất - tiền đã chuyển cho thợ
    FAILED,         // thanh toán thất bại
    REFUNDED        // hoàn tiền
}

enum class EscrowStatus {
    NONE,           // chưa có escrow (cash hoặc chưa thanh toán)
    HOLDING,        // hệ thống đang giữ tiền
    RELEASED,       // đã chuyển tiền cho thợ
    REFUNDED        // đã hoàn tiền cho khách
}