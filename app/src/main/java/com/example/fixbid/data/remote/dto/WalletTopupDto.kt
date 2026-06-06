package com.example.fixbid.data.remote.dto

import com.example.fixbid.core.utils.toEpochMillis
import com.example.fixbid.domain.model.WalletTopup
import com.example.fixbid.domain.model.WalletTopupStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors `public.wallet_topups`. Inserts go through `fn_create_wallet_topup`
 * (RLS forbids direct writes); reads come from a self-row RLS policy on the
 * table itself.
 */
@Serializable
data class WalletTopupDto(
    val id: String = "",
    @SerialName("user_id")        val userId: String = "",
    val amount: Double = 0.0,
    @SerialName("vnp_txn_ref")    val vnpTxnRef: String = "",
    @SerialName("transaction_id") val transactionId: String? = null,
    val status: String = "pending",
    @SerialName("response_code")  val responseCode: String? = null,
    @SerialName("completed_at")   val completedAt: String? = null,
    @SerialName("created_at")     val createdAt: String = "",
    @SerialName("updated_at")     val updatedAt: String = ""
) {
    fun toDomain() = WalletTopup(
        id            = id,
        userId        = userId,
        amount        = amount,
        vnpTxnRef     = vnpTxnRef,
        transactionId = transactionId,
        status        = runCatching {
            WalletTopupStatus.valueOf(status.uppercase())
        }.getOrDefault(WalletTopupStatus.PENDING),
        responseCode  = responseCode,
        completedAt   = completedAt?.toEpochMillis(),
        createdAt     = createdAt.toEpochMillis(),
        updatedAt     = updatedAt.toEpochMillis()
    )
}
