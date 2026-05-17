package com.example.fixbid.presentation.customer.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.BookingType
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class BookingUiState {
    object Idle : BookingUiState()
    object Loading : BookingUiState()
    object Success : BookingUiState()
    data class Error(val message: String) : BookingUiState()
}

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookingUiState>(BookingUiState.Idle)
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    private val _initialFullName = MutableStateFlow("")
    val initialFullName: StateFlow<String> = _initialFullName.asStateFlow()

    private val _initialPhone = MutableStateFlow("")
    val initialPhone: StateFlow<String> = _initialPhone.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser()?.let { user ->
                _initialFullName.value = user.fullName
                _initialPhone.value = user.phoneNumber ?: ""
            }
        }
    }

    fun createBooking(
        category: ServiceCategory,
        description: String,
        address: String,
        phoneNumber: String,
        fullName: String,
        notes: String
    ) {
        viewModelScope.launch {
            _uiState.value = BookingUiState.Loading
            val currentUser = authRepository.getCurrentUser()

            if (currentUser == null) {
                _uiState.value = BookingUiState.Error("Vui lòng đăng nhập để tiếp tục")
                return@launch
            }

            val customerNote = buildString {
                append("SĐT: $phoneNumber")
                append("\nTên: $fullName")
                if (notes.isNotBlank()) append("\nGhi chú: $notes")
            }

            val booking = Booking(
                id = UUID.randomUUID().toString(),
                customerId = currentUser.id,
                workerId = "",  // empty → toDto() sẽ convert thành null
                category = category,
                description = description,
                address = address,
                latitude = null,
                longitude = null,
                scheduledAt = System.currentTimeMillis(),
                estimatedDurationHours = 1.0,
                status = BookingStatus.BIDDING,
                type = BookingType.BIDDING,
                agreedPrice = null,
                customerNote = customerNote,
                workerNote = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            when (val result = bookingRepository.createBiddingBooking(booking)) {
                is Resource.Success -> {
                    _uiState.value = BookingUiState.Success
                }
                is Resource.Error -> {
                    _uiState.value = BookingUiState.Error(result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun resetState() {
        _uiState.value = BookingUiState.Idle
    }
}
