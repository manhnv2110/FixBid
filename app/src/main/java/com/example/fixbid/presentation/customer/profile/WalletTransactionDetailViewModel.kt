package com.example.fixbid.presentation.customer.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.WalletTopup
import com.example.fixbid.domain.model.WalletTransaction
import com.example.fixbid.domain.model.WalletTransactionType
import com.example.fixbid.domain.model.WalletWithdrawal
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import com.example.fixbid.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [WalletTransactionDetailScreen].
 *
 * The wallet ledger is the primary source — every type renders the same hero
 * (icon, signed amount, type label, timestamp). Type-specific blocks are
 * loaded lazily based on which links the row carries:
 *
 *  - `escrow_*` rows → look up the [Booking] + [Payment] so the screen can
 *    show the service category, address and worker info.
 *  - `topup` rows → look up the matching [WalletTopup] row by `reference`
 *    (= `vnp_txn_ref`) so the screen can show the VNPay status / response code.
 *  - `withdrawal_request` and `withdrawal` rows → look up the matching
 *    [WalletWithdrawal] by `reference` (= withdrawal id) so the screen can
 *    show the bank account snapshot + processing status.
 *
 * All side calls are best-effort: a failed enrichment doesn't break the
 * screen, the hero block always renders.
 */
data class WalletTransactionDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val transaction: WalletTransaction? = null,
    val booking: Booking? = null,
    val payment: Payment? = null,
    val topup: WalletTopup? = null,
    val withdrawal: WalletWithdrawal? = null
)

@HiltViewModel
class WalletTransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val walletRepository: WalletRepository,
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val transactionId: String = savedStateHandle.get<String>("transactionId") ?: ""

    private val _uiState = MutableStateFlow(WalletTransactionDetailUiState())
    val uiState: StateFlow<WalletTransactionDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (transactionId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Thiếu mã giao dịch") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = walletRepository.getMyTransactionById(transactionId)) {
                is Resource.Success -> {
                    val tx = result.data
                    _uiState.update { it.copy(transaction = tx, isLoading = false) }
                    enrich(tx)
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Pull the type-specific extra block. Each branch is wrapped in its own
     * coroutine so the slowest one doesn't block the rest, and any failure is
     * absorbed silently — the hero block from [WalletTransaction] always stays
     * on screen.
     */
    private fun enrich(tx: WalletTransaction) {
        when (tx.type) {
            WalletTransactionType.ESCROW_HOLD,
            WalletTransactionType.ESCROW_RELEASE,
            WalletTransactionType.ESCROW_REFUND -> loadBookingAndPayment(tx.bookingId)

            WalletTransactionType.TOPUP -> loadTopup(tx.reference)

            WalletTransactionType.WITHDRAWAL_REQUEST,
            WalletTransactionType.WITHDRAWAL -> loadWithdrawal(tx.reference)

            WalletTransactionType.ADJUSTMENT -> Unit // no extra block
        }
    }

    private fun loadBookingAndPayment(bookingId: String?) {
        if (bookingId.isNullOrBlank()) return
        viewModelScope.launch {
            (bookingRepository.getBookingById(bookingId) as? Resource.Success)?.let { r ->
                _uiState.update { it.copy(booking = r.data) }
            }
            (paymentRepository.getPaymentByBooking(bookingId) as? Resource.Success)?.let { r ->
                _uiState.update { it.copy(payment = r.data) }
            }
        }
    }

    private fun loadTopup(vnpTxnRef: String?) {
        if (vnpTxnRef.isNullOrBlank()) return
        viewModelScope.launch {
            (walletRepository.getTopupByTxnRef(vnpTxnRef) as? Resource.Success)?.let { r ->
                _uiState.update { it.copy(topup = r.data) }
            }
        }
    }

    private fun loadWithdrawal(withdrawalId: String?) {
        if (withdrawalId.isNullOrBlank()) return
        viewModelScope.launch {
            (walletRepository.getWithdrawalById(withdrawalId) as? Resource.Success)?.let { r ->
                _uiState.update { it.copy(withdrawal = r.data) }
            }
        }
    }
}
