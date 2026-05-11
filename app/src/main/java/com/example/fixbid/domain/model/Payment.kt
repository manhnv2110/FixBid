package com.example.fixbid.domain.model

data class Payment(
    val id: String,
    val bookingId: String,
    val customerId: String,
    val workerId: String,
    val amount: Double,
    val platformFee: Double,           // phí app (ví dụ 10%)
    val workerReceives: Double,        // amount - platformFee
    val method: PaymentMethod,
    val status: PaymentStatus,
    val transactionId: String?,        // mã từ cổng thanh toán
    val paidAt: Long?,
    val createdAt: Long
)

enum class PaymentMethod {
    CASH,           // tiền mặt (xác nhận thủ công)
    MOMO,
    VNPAY,
    BANK_TRANSFER
}

enum class PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED
}