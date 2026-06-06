package com.example.fixbid.domain.model

/**
 * A withdrawal request the customer submits from the wallet screen. The
 * actual bank transfer happens off-app; this row is the source of truth for
 * the locked-vs-released amount and the bank account snapshot used by ops.
 *
 * Submitting a request locks `amount` from `wallets.balance` into
 * `wallets.pending_balance` via `fn_request_wallet_withdrawal`. Ops then
 * either marks the request COMPLETED (drain pending → out of system) or
 * REJECTED (refund pending → balance).
 */
data class WalletWithdrawal(
    val id: String,
    val userId: String,
    val amount: Double,
    val bankName: String,
    val bankAccountNumber: String,
    val bankAccountHolder: String,
    val note: String?,
    val status: WalletWithdrawalStatus,
    val rejectionReason: String?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class WalletWithdrawalStatus {
    PROCESSING,  // user submitted; ops handling the bank transfer
    COMPLETED,   // money has been disbursed
    REJECTED,    // ops declined and the lock has been refunded
    CANCELLED    // reserved for a future "user cancels in-flight" action
}
