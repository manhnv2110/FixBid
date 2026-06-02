package com.example.fixbid.presentation.worker.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Wallet
import com.example.fixbid.domain.model.WalletTransaction
import com.example.fixbid.domain.model.WalletTransactionType
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Worker wallet view model backed by the real `wallets` + `wallet_transactions`
 * tables (see migration `20260603_wallets.sql`). The previous version derived
 * balances from the `payments` table on the fly; now we read them from a
 * single source of truth that's mutated atomically by Postgres RPCs.
 *
 * Each wallet transaction is paired with a thin booking lookup so the UI can
 * show the service name and customer name alongside the line entry.
 */
data class WalletLedgerRow(
    val transaction: WalletTransaction,
    val bookingTitle: String?,
    val customerName: String?
)

data class WorkerWalletUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val wallet: Wallet? = null,
    val transactions: List<WalletLedgerRow> = emptyList()
) {
    val balance: Double get() = wallet?.balance ?: 0.0
    val pendingBalance: Double get() = wallet?.pendingBalance ?: 0.0
    val totalEarned: Double get() = wallet?.totalEarned ?: 0.0
    val totalWithdrawn: Double get() = wallet?.totalWithdrawn ?: 0.0

    val monthlyEarnings: Double
        get() {
            val zone = ZoneId.systemDefault()
            val thisMonth = YearMonth.now(zone)
            return transactions
                .filter { it.transaction.type == WalletTransactionType.ESCROW_RELEASE }
                .filter { row ->
                    val ts = row.transaction.createdAt
                    if (ts <= 0L) false
                    else YearMonth.from(java.time.Instant.ofEpochMilli(ts).atZone(zone)) == thisMonth
                }
                .sumOf { it.transaction.amount }
        }

    val payoutCount: Int
        get() = transactions.count { it.transaction.type == WalletTransactionType.ESCROW_RELEASE }
}

@HiltViewModel
class WorkerWalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bookingRepository: BookingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerWalletUiState())
    val uiState: StateFlow<WorkerWalletUiState> = _uiState.asStateFlow()

    init {
        load()
        observe()
    }

    fun refresh() = load(refresh = true)

    private fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refresh,
                    isRefreshing = refresh,
                    errorMessage = null
                )
            }

            val walletResult = walletRepository.getMyWallet()
            if (walletResult is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = walletResult.message
                    )
                }
                return@launch
            }
            val wallet = (walletResult as Resource.Success).data

            val txResult = walletRepository.getMyTransactions()
            val txs = (txResult as? Resource.Success)?.data ?: emptyList()

            // Enrich each ledger row with its source booking so the list
            // can render service name + customer without round-tripping.
            // Only one fetch per unique bookingId to keep the network cost
            // proportional to distinct jobs, not transactions.
            val bookingsCache = mutableMapOf<String, Booking?>()
            val rows = txs.map { tx ->
                val booking = tx.bookingId?.let { bookingId ->
                    bookingsCache.getOrPut(bookingId) {
                        (bookingRepository.getBookingById(bookingId) as? Resource.Success)?.data
                    }
                }
                WalletLedgerRow(
                    transaction = tx,
                    bookingTitle = booking?.category?.displayName,
                    customerName = booking?.customer?.fullName
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    wallet = wallet,
                    transactions = rows,
                    errorMessage = if (txResult is Resource.Error) txResult.message else null
                )
            }
        }
    }

    private fun observe() {
        // Pump realtime updates into the snapshot so the available balance
        // reflects immediately when an escrow release lands while the wallet
        // is open. We don't refetch the ledger here — the wallet update is
        // enough to nudge the user; pull-to-refresh refreshes everything.
        viewModelScope.launch {
            walletRepository.observeMyWallet().collect { wallet ->
                if (wallet != null) {
                    _uiState.update { it.copy(wallet = wallet) }
                }
            }
        }
    }
}
