package com.example.fixbid.presentation.customer.completion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.customer.ConfirmCompletionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompletionConfirmUiState(
    val isLoading: Boolean = true,
    val booking: Booking? = null,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val showRejectDialog: Boolean = false,
    val rejectReason: String = ""
)

sealed class CompletionConfirmEvent {
    data class Toast(val message: String) : CompletionConfirmEvent()
    object CompletionConfirmed : CompletionConfirmEvent()
    object CompletionRejected : CompletionConfirmEvent()
}

@HiltViewModel
class CompletionConfirmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingRepository: BookingRepository,
    private val confirmCompletionUseCase: ConfirmCompletionUseCase
) : ViewModel() {

    private val bookingId: String = savedStateHandle.get<String>("bookingId") ?: ""

    private val _uiState = MutableStateFlow(CompletionConfirmUiState())
    val uiState: StateFlow<CompletionConfirmUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CompletionConfirmEvent>()
    val events: SharedFlow<CompletionConfirmEvent> = _events.asSharedFlow()

    init {
        loadBooking()
    }

    fun loadBooking() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = bookingRepository.getBookingById(bookingId)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        booking = result.data
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

    fun confirmCompletion() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)
            when (val result = confirmCompletionUseCase.confirm(bookingId)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                    _events.emit(CompletionConfirmEvent.Toast("Đã xác nhận hoàn thành!"))
                    _events.emit(CompletionConfirmEvent.CompletionConfirmed)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                    _events.emit(CompletionConfirmEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun openRejectDialog() {
        _uiState.value = _uiState.value.copy(showRejectDialog = true)
    }

    fun closeRejectDialog() {
        _uiState.value = _uiState.value.copy(showRejectDialog = false, rejectReason = "")
    }

    fun onRejectReasonChange(reason: String) {
        _uiState.value = _uiState.value.copy(rejectReason = reason)
    }

    fun submitReject() {
        val reason = _uiState.value.rejectReason.trim()
        if (reason.isBlank()) {
            viewModelScope.launch {
                _events.emit(CompletionConfirmEvent.Toast("Vui lòng nhập lý do từ chối"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)
            when (val result = confirmCompletionUseCase.reject(bookingId, reason)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        showRejectDialog = false
                    )
                    _events.emit(CompletionConfirmEvent.Toast("Đã từ chối. Thợ sẽ tiếp tục công việc."))
                    _events.emit(CompletionConfirmEvent.CompletionRejected)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                    _events.emit(CompletionConfirmEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }
}