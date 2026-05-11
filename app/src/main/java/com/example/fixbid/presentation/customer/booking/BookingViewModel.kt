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
                // Should normally handle this by redirecting to login, but for now just mock or error
                _uiState.value = BookingUiState.Error("Vui lòng đăng nhập để tiếp tục")
                return@launch
            }

            val finalDescription = description

            val booking = Booking(
                id = UUID.randomUUID().toString(),
                customerId = currentUser.id,
                workerId = "", // Empty for bidding
                category = category,
                description = finalDescription,
                address = address,
                latitude = null, // Can integrate Maps later
                longitude = null,
                scheduledAt = System.currentTimeMillis(), // Or a date picker value
                estimatedDurationHours = 1.0,
                status = BookingStatus.BIDDING,
                type = BookingType.BIDDING,
                agreedPrice = null,
                customerNote = "SĐT: $phoneNumber, Tên: $fullName\nGhi chú: $notes",
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

                else -> {}
            }
        }
    }

    fun resetState() {
        _uiState.value = BookingUiState.Idle
    }
}
