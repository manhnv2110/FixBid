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
) {
    /** Id của người đối diện so với [currentUserId] (khách ↔ thợ). */
    fun counterpartId(currentUserId: String): String =
        if (currentUserId == customerId) workerId else customerId

    /** Thông tin người đối diện đã được enrich (tên/avatar) để hiển thị. */
    val counterpart: User? get() = worker ?: customer
}
