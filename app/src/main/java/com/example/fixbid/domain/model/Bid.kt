package com.example.fixbid.domain.model

data class Bid(
    val id: String,
    val bookingId: String,
    val workerId: String,
    val proposedPrice: Double,
    val estimatedDurationHours: Double,
    val message: String,               // thợ giới thiệu bản thân / giải thích giá
    val status: BidStatus,
    val createdAt: Long,
    val worker: User? = null           // load kèm để hiển thị profile thợ
)

enum class BidStatus {
    PENDING,    // đang chờ khách xét
    ACCEPTED,   // khách chọn bid này
    REJECTED,   // khách không chọn
    WITHDRAWN   // thợ rút bid
}