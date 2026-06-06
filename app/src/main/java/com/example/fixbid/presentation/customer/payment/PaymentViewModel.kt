package com.example.fixbid.presentation.customer.payment

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import com.example.fixbid.domain.usecase.customer.CreateVNPayPaymentUseCase
import com.example.fixbid.domain.usecase.customer.ProcessVNPayReturnUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentUiState(
    val isLoading: Boolean = true,
    val booking: Booking? = null,
    val payment: Payment? = null,
    val paymentUrl: String? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val paymentSuccess: Boolean = false
)

sealed class PaymentEvent {
    data class Toast(val message: String) : PaymentEvent()
    data class OpenVNPayUrl(val url: String) : PaymentEvent()
    object PaymentCompleted : PaymentEvent()
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val createVNPayPaymentUseCase: CreateVNPayPaymentUseCase,
    private val processVNPayReturnUseCase: ProcessVNPayReturnUseCase
) : ViewModel() {

    private val bookingId: String = savedStateHandle.get<String>("bookingId") ?: ""

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PaymentEvent>()
    val events: SharedFlow<PaymentEvent> = _events.asSharedFlow()

    init {
        loadBookingDetails()
    }

    fun loadBookingDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = bookingRepository.getBookingById(bookingId)) {
                is Resource.Success -> {
                    val booking = result.data
                    // Kiểm tra xem đã có payment chưa
                    val existingPayment = paymentRepository.getPaymentByBooking(bookingId)
                    val payment = (existingPayment as? Resource.Success)?.data

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        booking = booking,
                        payment = payment,
                        paymentSuccess = payment?.status == PaymentStatus.ESCROW ||
                                payment?.status == PaymentStatus.COMPLETED
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Khởi tạo thanh toán VNPay:
     * - Tạo payment record
     * - Generate URL
     * - Emit event để mở WebView/Browser
     */
    fun initiateVNPayPayment() {
        val booking = _uiState.value.booking ?: return
        val amount = booking.agreedPrice ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)

            when (val result = createVNPayPaymentUseCase(
                bookingId = bookingId,
                amount = amount
            )) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        payment = result.data.payment,
                        paymentUrl = result.data.paymentUrl
                    )
                    _events.emit(PaymentEvent.OpenVNPayUrl(result.data.paymentUrl))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = result.message
                    )
                    _events.emit(PaymentEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Xử lý deep link callback từ VNPay.
     * Được gọi khi app nhận được redirect URL: fixbid://vnpay-return?vnp_...
     */
    fun handleVNPayReturn(uri: Uri) {
        val params = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { value ->
                params[key] = value
            }
        }

        if (params.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)

            when (val result = processVNPayReturnUseCase(params)) {
                is Resource.Success -> {
                    // The use case now returns either a booking-payment or a
                    // top-up branch. This screen only handles booking payments;
                    // refetch the payment row to get the latest snapshot rather
                    // than carrying the result through (top-ups never land here
                    // because the deep link is consumed by VNPayReturnScreen).
                    val refreshed = (paymentRepository.getPaymentByBooking(bookingId)
                        as? Resource.Success)?.data
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        payment = refreshed ?: _uiState.value.payment,
                        paymentSuccess = result.data is ProcessVNPayReturnUseCase.Result.BookingPayment
                    )
                    _events.emit(PaymentEvent.Toast("Thanh toán thành công! Tiền đang được giữ an toàn."))
                    _events.emit(PaymentEvent.PaymentCompleted)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = result.message
                    )
                    _events.emit(PaymentEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }
}