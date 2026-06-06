package com.example.fixbid.domain.model

/**
 * A single VNPay top-up attempt. Created when the customer submits the
 * "Nạp tiền" form, then transitioned by [com.example.fixbid.data.remote.vnpay.VNPayService]'s
 * return handler to either `COMPLETED` (balance credited via
 * `fn_credit_wallet_topup`) or `FAILED`.
 *
 * `vnpTxnRef` is the idempotency key shipped to VNPay as `vnp_TxnRef`. Format:
 * `TOPUP-<wallet_topup.id>` so the return handler can route by prefix.
 */
data class WalletTopup(
    val id: String,
    val userId: String,
    val amount: Double,
    val vnpTxnRef: String,
    val transactionId: String?,
    val status: WalletTopupStatus,
    val responseCode: String?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class WalletTopupStatus {
    PENDING,    // VNPay URL generated, awaiting return
    COMPLETED,  // wallet credited
    FAILED,     // VNPay declined the transaction
    CANCELLED   // user backed out before paying
}
