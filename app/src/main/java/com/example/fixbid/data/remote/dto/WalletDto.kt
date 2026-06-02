package com.example.fixbid.data.remote.dto

import com.example.fixbid.core.utils.toEpochMillis
import com.example.fixbid.domain.model.Wallet
import com.example.fixbid.domain.model.WalletTransaction
import com.example.fixbid.domain.model.WalletTransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalletDto(
    val id: String = "",
    @SerialName("user_id")          val userId: String = "",
    val balance: Double = 0.0,
    @SerialName("pending_balance")  val pendingBalance: Double = 0.0,
    @SerialName("total_earned")     val totalEarned: Double = 0.0,
    @SerialName("total_withdrawn")  val totalWithdrawn: Double = 0.0,
    @SerialName("created_at")       val createdAt: String = "",
    @SerialName("updated_at")       val updatedAt: String = ""
) {
    fun toDomain() = Wallet(
        id              = id,
        userId          = userId,
        balance         = balance,
        pendingBalance  = pendingBalance,
        totalEarned     = totalEarned,
        totalWithdrawn  = totalWithdrawn,
        createdAt       = createdAt.toEpochMillis(),
        updatedAt       = updatedAt.toEpochMillis()
    )
}

@Serializable
data class WalletTransactionDto(
    val id: String = "",
    @SerialName("wallet_id")               val walletId: String = "",
    @SerialName("user_id")                 val userId: String = "",
    val type: String = "adjustment",
    val amount: Double = 0.0,
    @SerialName("balance_after")           val balanceAfter: Double = 0.0,
    @SerialName("pending_balance_after")   val pendingBalanceAfter: Double = 0.0,
    @SerialName("booking_id")              val bookingId: String? = null,
    @SerialName("payment_id")              val paymentId: String? = null,
    val description: String? = null,
    val reference: String? = null,
    @SerialName("created_at")              val createdAt: String = ""
) {
    fun toDomain() = WalletTransaction(
        id                  = id,
        walletId            = walletId,
        userId              = userId,
        type                = runCatching {
            WalletTransactionType.valueOf(type.uppercase())
        }.getOrDefault(WalletTransactionType.ADJUSTMENT),
        amount              = amount,
        balanceAfter        = balanceAfter,
        pendingBalanceAfter = pendingBalanceAfter,
        bookingId           = bookingId,
        paymentId           = paymentId,
        description         = description,
        reference           = reference,
        createdAt           = createdAt.toEpochMillis()
    )
}
