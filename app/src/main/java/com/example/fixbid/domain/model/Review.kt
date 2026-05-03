package com.example.fixbid.domain.model

data class Review(
    val id: String,
    val bookingId: String,
    val customerId: String,
    val workerId: String,
    val rating: Int,                   // 1-5 sao
    val comment: String?,
    val imageUrls: List<String>,       // ảnh kết quả công việc
    val workerReply: String?,          // thợ có thể phản hồi review
    val createdAt: Long,
    val customer: User? = null
)