package com.example.fixbid.presentation.worker.jobdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.usecase.worker.GetJobDetailUseCase
import com.example.fixbid.domain.usecase.worker.JobDetailData
import com.example.fixbid.domain.usecase.worker.PlaceBidUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BidFormState(
    val price: String = "",
    val durationHours: String = "",
    val message: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

data class JobDetailUiState(
    val isLoading: Boolean = true,
    val data: JobDetailData? = null,
    val errorMessage: String? = null,
    val showBidDialog: Boolean = false,
    val bidForm: BidFormState = BidFormState()
)

sealed interface JobDetailEvent {
    data class Toast(val message: String) : JobDetailEvent
    data object BidPlaced : JobDetailEvent
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getJobDetailUseCase: GetJobDetailUseCase,
    private val placeBidUseCase: PlaceBidUseCase
) : ViewModel() {

    private val bookingId: String = savedStateHandle.get<String>("bookingId") ?: ""

    private val _uiState = MutableStateFlow(JobDetailUiState())
    val uiState: StateFlow<JobDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<JobDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = getJobDetailUseCase(bookingId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, data = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    fun openBidDialog() {
        val booking = _uiState.value.data?.booking ?: return
        val suggestedPrice = booking.agreedPrice?.let { it.toLong().toString() } ?: ""
        val suggestedDuration = booking.estimatedDurationHours.takeIf { it > 0 }
            ?.toString() ?: ""
        _uiState.update {
            it.copy(
                showBidDialog = true,
                bidForm = BidFormState(
                    price = suggestedPrice,
                    durationHours = suggestedDuration,
                    message = ""
                )
            )
        }
    }

    fun closeBidDialog() = _uiState.update {
        it.copy(showBidDialog = false, bidForm = BidFormState())
    }

    fun onPriceChange(value: String) = _uiState.update {
        val sanitized = value.filter { c -> c.isDigit() }
        it.copy(bidForm = it.bidForm.copy(price = sanitized, errorMessage = null))
    }

    fun onDurationChange(value: String) = _uiState.update {
        val sanitized = value.filter { c -> c.isDigit() || c == '.' || c == ',' }
            .replace(',', '.')
        it.copy(bidForm = it.bidForm.copy(durationHours = sanitized, errorMessage = null))
    }

    fun onMessageChange(value: String) = _uiState.update {
        it.copy(bidForm = it.bidForm.copy(message = value, errorMessage = null))
    }

    fun submitBid() {
        val form = _uiState.value.bidForm
        if (form.isSubmitting) return

        val price = form.price.toDoubleOrNull()
        val duration = form.durationHours.toDoubleOrNull()

        when {
            price == null || price <= 0 -> setBidError("Vui lòng nhập giá hợp lệ")
            duration == null || duration <= 0 -> setBidError("Vui lòng nhập thời gian dự kiến")
            form.message.trim().length < 10 -> setBidError("Lời giới thiệu cần ít nhất 10 ký tự")
            else -> {
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(bidForm = it.bidForm.copy(isSubmitting = true, errorMessage = null))
                    }
                    when (val result = placeBidUseCase(
                        bookingId = bookingId,
                        proposedPrice = price,
                        estimatedDurationHours = duration,
                        message = form.message
                    )) {
                        is Resource.Success -> {
                            _uiState.update {
                                it.copy(
                                    showBidDialog = false,
                                    bidForm = BidFormState()
                                )
                            }
                            _events.trySend(JobDetailEvent.Toast("Đã gửi báo giá"))
                            _events.trySend(JobDetailEvent.BidPlaced)
                            load()
                        }
                        is Resource.Error -> {
                            _uiState.update {
                                it.copy(
                                    bidForm = it.bidForm.copy(
                                        isSubmitting = false,
                                        errorMessage = result.message
                                    )
                                )
                            }
                        }
                        is Resource.Loading -> { /* no-op */ }
                    }
                }
            }
        }
    }

    private fun setBidError(msg: String) = _uiState.update {
        it.copy(bidForm = it.bidForm.copy(errorMessage = msg))
    }
}
