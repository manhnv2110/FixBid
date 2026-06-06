package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Wallet
import com.example.fixbid.domain.model.WalletTopup
import com.example.fixbid.domain.model.WalletTransaction
import com.example.fixbid.domain.model.WalletWithdrawal
import kotlinx.coroutines.flow.Flow

interface WalletRepository {

    /**
     * Fetch the signed-in user's wallet. Lazily creates the wallet row on
     * first call so workers who signed up before the wallet feature shipped
     * still see a valid (zeroed) snapshot instead of a "not found" error.
     */
    suspend fun getMyWallet(): Resource<Wallet>

    /** Convenience overload for callers that already know the user id. */
    suspend fun getWalletByUser(userId: String): Resource<Wallet>

    suspend fun getMyTransactions(limit: Int = 100): Resource<List<WalletTransaction>>

    /** Fetch a single ledger row owned by the signed-in user. */
    suspend fun getMyTransactionById(id: String): Resource<WalletTransaction>

    /**
     * Move `worker_receives` from the worker's own funds into the pending
     * bucket. Idempotent on [paymentId] — calling twice is a no-op.
     */
    suspend fun holdEscrow(paymentId: String): Resource<Wallet>

    /**
     * Release a previously held escrow into the worker's available balance.
     * Idempotent on [paymentId].
     */
    suspend fun releaseEscrow(paymentId: String): Resource<Wallet>

    /**
     * Atomically transfer the escrowed funds back to the customer when the
     * worker cancels: deducts `worker_receives` from the worker's
     * `pending_balance` and credits `payment.amount` (full gross, including
     * platform fee) to the customer's `balance`. Idempotent on [paymentId] —
     * calling twice returns the same worker snapshot without double-debiting.
     *
     * Errors when the payment is not in `HOLDING` state.
     *
     * Returns the worker wallet snapshot after the refund.
     */
    suspend fun refundEscrowToCustomer(paymentId: String): Resource<Wallet>

    /** Realtime stream of wallet updates for the signed-in worker. */
    fun observeMyWallet(): Flow<Wallet?>

    // ─── Top-up (VNPay → wallet.balance) ──────────────────────────────────

    /**
     * Create a `wallet_topups` row in PENDING and return it. The caller
     * (Kotlin layer) then builds the VNPay URL with `vnp_TxnRef = TOPUP-<id>`
     * and redirects the user. Settlement is done by [creditWalletTopup] or
     * [failWalletTopup] from the VNPay return handler.
     */
    suspend fun createTopup(amount: Double, vnpTxnRef: String): Resource<WalletTopup>

    /**
     * Credit the wallet for a successful VNPay top-up. Idempotent on
     * `vnp_txn_ref`: calling twice does not double-credit.
     *
     * Lookup is by `vnp_txn_ref` (not the row id) because the gateway only
     * echoes back what we shipped as `vnp_TxnRef` and the client never sees
     * the generated `wallet_topups.id`.
     */
    suspend fun creditWalletTopup(vnpTxnRef: String, transactionId: String): Resource<Wallet>

    /** Mark a pending top-up as failed (best-effort, ignores not-pending). */
    suspend fun failWalletTopup(vnpTxnRef: String, responseCode: String): Resource<Unit>

    /**
     * History of the signed-in user's top-ups (most recent first). Optional —
     * surfaced in the wallet history list under "Đã nạp".
     */
    suspend fun getMyTopups(limit: Int = 50): Resource<List<WalletTopup>>

    /** Look up a single top-up by `vnp_txn_ref` (the value VNPay echoes back). */
    suspend fun getTopupByTxnRef(vnpTxnRef: String): Resource<WalletTopup>

    // ─── Withdrawal (wallet.balance → bank account, off-app) ──────────────

    /**
     * Submit a withdrawal request. Locks `amount` from `wallets.balance` into
     * `wallets.pending_balance` and inserts a `wallet_withdrawals` row in
     * PROCESSING. The actual bank transfer happens off-app; ops eventually
     * settles or rejects the request.
     */
    suspend fun requestWithdrawal(
        amount: Double,
        bankName: String,
        bankAccountNumber: String,
        bankAccountHolder: String,
        note: String?
    ): Resource<WalletWithdrawal>

    /** History of the signed-in user's withdrawal requests (most recent first). */
    suspend fun getMyWithdrawals(limit: Int = 50): Resource<List<WalletWithdrawal>>

    /** Look up a single withdrawal by id. */
    suspend fun getWithdrawalById(id: String): Resource<WalletWithdrawal>
}
