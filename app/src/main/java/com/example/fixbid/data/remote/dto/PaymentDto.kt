package com.example.fixbid.data.remote.dto

import com.example.fixbid.core.utils.toEpochMillis
import com.example.fixbid.domain.model.EscrowStatus
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentMethod
import com.example.fixbid.domain.model.PaymentStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentDto(
    val id: String = "",
    @SerialName("booking_id")       val bookingId: String = "",
    @SerialName("customer_id")      val customerId: String = "",
    @SerialName("worker_id")        val workerId: String = "",
    val amount: Double = 0.0,
    @SerialName("platform_fee")     val platformFee: Double = 0.0,
    @SerialName("worker_receives")  val workerReceives: Double = 0.0,
    val method: String = "cash",
    val status: String = "pending",
    @SerialName("transaction_id")   val transactionId: String? = null,
    @SerialName("paid_at")          val paidAt: String? = null,
    @SerialName("released_at")      val releasedAt: String? = null,
    @SerialName("escrow_status")    val escrowStatus: String = "none",
    @SerialName("created_at")       val createdAt: String = ""
) {
    fun toDomain() = Payment(
        id             = id,
        bookingId      = bookingId,
        customerId     = customerId,
        workerId       = workerId,
        amount         = amount,
        platformFee    = platformFee,
        workerReceives = workerReceives,
        method         = runCatching { PaymentMethod.valueOf(method.uppercase()) }
            .getOrDefault(PaymentMethod.CASH),
        status         = runCatching { PaymentStatus.valueOf(status.uppercase()) }
            .getOrDefault(PaymentStatus.PENDING),
        transactionId  = transactionId,
        paidAt         = paidAt?.toEpochMillis(),
        releasedAt     = releasedAt?.toEpochMillis(),
        escrowStatus   = runCatching { EscrowStatus.valueOf(escrowStatus.uppercase()) }
            .getOrDefault(EscrowStatus.NONE),
        createdAt      = createdAt.toEpochMillis()
    )
}

fun Payment.toDto() = PaymentDto(
    id             = id,
    bookingId      = bookingId,
    customerId     = customerId,
    workerId       = workerId,
    amount         = amount,
    platformFee    = platformFee,
    workerReceives = workerReceives,
    method         = method.name.lowercase(),
    status         = status.name.lowercase(),
    transactionId  = transactionId,
    escrowStatus   = escrowStatus.name.lowercase()
)