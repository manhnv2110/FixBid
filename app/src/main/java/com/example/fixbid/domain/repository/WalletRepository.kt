package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Wallet
import com.example.fixbid.domain.model.WalletTransaction
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

    /** Realtime stream of wallet updates for the signed-in worker. */
    fun observeMyWallet(): Flow<Wallet?>
}
