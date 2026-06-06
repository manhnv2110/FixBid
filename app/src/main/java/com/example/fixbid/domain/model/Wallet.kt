package com.example.fixbid.domain.model

/**
 * In-app wallet snapshot for a worker.
 *
 * `balance` is the spendable amount (escrow already released). `pendingBalance`
 * is what's currently being held by the platform — money the customer has
 * already paid but hasn't been released yet because the job isn't confirmed
 * complete.
 */
data class Wallet(
    val id: String,
    val userId: String,
    val balance: Double,
    val pendingBalance: Double,
    val totalEarned: Double,
    val totalWithdrawn: Double,
    val createdAt: Long,
    val updatedAt: Long
)

/** Direction the [WalletTransaction] moved money in the wallet ledger. */
enum class WalletTransactionType {
    ESCROW_HOLD,         // pending++ when VNPay marks the payment as escrow
    ESCROW_RELEASE,      // pending-- and balance++ when customer confirms completion
    ESCROW_REFUND,       // pending-- when a payment is refunded back to the customer
    WITHDRAWAL,          // balance-- when an admin completes a withdrawal request
    WITHDRAWAL_REQUEST,  // balance-- + pending++ when the user submits a withdrawal
    TOPUP,               // balance++ when a VNPay top-up settles
    ADJUSTMENT           // ops manual correction (signed via amount + description)
}

/**
 * Append-only ledger entry. The wallet row at the time of writing is
 * captured in [balanceAfter] / [pendingBalanceAfter] so historical UI
 * reproductions are deterministic.
 */
data class WalletTransaction(
    val id: String,
    val walletId: String,
    val userId: String,
    val type: WalletTransactionType,
    val amount: Double,
    val balanceAfter: Double,
    val pendingBalanceAfter: Double,
    val bookingId: String?,
    val paymentId: String?,
    val description: String?,
    val reference: String?,
    val createdAt: Long
)
