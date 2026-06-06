package com.example.fixbid.presentation.customer.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Wallet
import com.example.fixbid.domain.model.WalletTransaction
import com.example.fixbid.domain.repository.WalletRepository
import com.example.fixbid.domain.usecase.customer.CreateWalletTopupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Customer-side wallet view model.
 *
 * Mirrors [com.example.fixbid.presentation.worker.wallet.WorkerWalletViewModel]
 * but trims the worker-only enrichment (per-transaction booking + customer name lookups) since
 * customer-side ledger rows already carry a self-explanatory `description` from the refund RPC.
 *
 * The wallet snapshot drives the hero card balance and the ledger list reflects
 * `wallet_transactions` filtered to the signed-in user. A realtime subscription on
 * [WalletRepository.observeMyWallet] keeps the displayed balance fresh without manual refresh
 * — Requirement 6.7.
 *
 * Top-up + withdraw flows are handled here too: the screen opens the relevant
 * sheet, the VM calls the use case / repository, and emits [WalletEvent.OpenTopupUrl]
 * for the screen to launch a browser intent. The VNPay return handler (a separate
 * deep-link route) is responsible for actually crediting the wallet.
 */
data class CustomerWalletUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val wallet: Wallet? = null,
    val transactions: List<WalletTransaction> = emptyList(),

    // Top-up sheet state
    val showTopupSheet: Boolean = false,
    val isTopupSubmitting: Boolean = false,
    val topupError: String? = null,

    // Withdraw sheet state
    val showWithdrawSheet: Boolean = false,
    val isWithdrawSubmitting: Boolean = false,
    val withdrawError: String? = null
)

sealed interface WalletEvent {
    data class OpenTopupUrl(val url: String) : WalletEvent
    data class Toast(val message: String) : WalletEvent
}

@HiltViewModel
class CustomerWalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val createWalletTopupUseCase: CreateWalletTopupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerWalletUiState())
    val uiState: StateFlow<CustomerWalletUiState> = _uiState.asStateFlow()

    private val _events = Channel<WalletEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
        observe()
    }

    /** Re-fetch wallet + transactions. Used by the "Thử lại" button and pull-to-refresh. */
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

            val txResult = walletRepository.getMyTransactions(limit = 100)
            val txs = (txResult as? Resource.Success)?.data ?: emptyList()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    wallet = wallet,
                    transactions = txs,
                    errorMessage = if (txResult is Resource.Error) txResult.message else null
                )
            }
        }
    }

    private fun observe() {
        viewModelScope.launch {
            walletRepository.observeMyWallet().collect { wallet ->
                if (wallet != null) {
                    _uiState.update { it.copy(wallet = wallet) }
                }
            }
        }
    }

    // ─── Top-up ───────────────────────────────────────────────────────────

    fun openTopupSheet() = _uiState.update {
        it.copy(showTopupSheet = true, topupError = null)
    }

    fun closeTopupSheet() = _uiState.update {
        it.copy(showTopupSheet = false, isTopupSubmitting = false, topupError = null)
    }

    /**
     * Create the topup row + build the VNPay URL, then emit it so the screen
     * can launch a browser intent. The sheet is closed only after the URL has
     * been emitted to avoid stranding the user with no progress indicator.
     */
    fun submitTopup(amount: Double) {
        if (_uiState.value.isTopupSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTopupSubmitting = true, topupError = null) }
            when (val result = createWalletTopupUseCase(amount)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isTopupSubmitting = false,
                            showTopupSheet = false,
                            topupError = null
                        )
                    }
                    _events.trySend(WalletEvent.OpenTopupUrl(result.data.paymentUrl))
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isTopupSubmitting = false, topupError = result.message)
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ─── Withdraw ─────────────────────────────────────────────────────────

    fun openWithdrawSheet() = _uiState.update {
        it.copy(showWithdrawSheet = true, withdrawError = null)
    }

    fun closeWithdrawSheet() = _uiState.update {
        it.copy(showWithdrawSheet = false, isWithdrawSubmitting = false, withdrawError = null)
    }

    fun submitWithdraw(
        amount: Double,
        bankName: String,
        bankAccountNumber: String,
        bankAccountHolder: String,
        note: String?
    ) {
        if (_uiState.value.isWithdrawSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWithdrawSubmitting = true, withdrawError = null) }
            when (val result = walletRepository.requestWithdrawal(
                amount = amount,
                bankName = bankName,
                bankAccountNumber = bankAccountNumber,
                bankAccountHolder = bankAccountHolder,
                note = note
            )) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isWithdrawSubmitting = false,
                            showWithdrawSheet = false,
                            withdrawError = null
                        )
                    }
                    _events.trySend(WalletEvent.Toast("Đã gửi yêu cầu rút tiền — chờ xử lý"))
                    // Refresh wallet + ledger so the locked amount is visible
                    // without waiting for the realtime echo.
                    load(refresh = true)
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isWithdrawSubmitting = false,
                            withdrawError = result.message
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }
}
