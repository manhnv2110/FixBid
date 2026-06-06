package com.example.fixbid.presentation.customer.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.usecase.customer.GetMyBookingsUseCase
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Success(
        val activeBookings: List<Booking>,
        val completedBookings: List<Booking>,
        val cancelledBookings: List<Booking>,
        val reviewedBookingIds: Set<String> = emptySet()
    ) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}

@HiltViewModel
class BookingHistoryViewModel @Inject constructor(
    private val getMyBookingsUseCase: GetMyBookingsUseCase,
    private val authRepository: AuthRepository,
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var hasLoadedOnce = false

    init {
        loadBookings()
    }

    /**
     * Load lần đầu — hiện full loading spinner.
     */
    fun loadBookings() {
        viewModelScope.launch {
            if (!hasLoadedOnce) {
                _uiState.value = HistoryUiState.Loading
            }
            fetchBookings()
            hasLoadedOnce = true
        }
    }

    /**
     * Refresh ngầm — không hiện loading spinner, chỉ cập nhật data.
     * Dùng cho pull-to-refresh và auto-refresh khi quay lại screen.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchBookings()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchBookings() {
        when (val result = getMyBookingsUseCase()) {
            is Resource.Success -> {
                val all = result.data
                val active = all.filter {
                    it.status in listOf(
                        BookingStatus.BIDDING,
                        BookingStatus.PENDING,
                        BookingStatus.AWAITING_PAYMENT,
                        BookingStatus.CONFIRMED,
                        BookingStatus.IN_PROGRESS,
                        BookingStatus.PENDING_COMPLETION
                    )
                }.sortedByDescending { it.createdAt }

                val completed = all.filter {
                    it.status == BookingStatus.COMPLETED
                }.sortedByDescending { it.createdAt }

                // CANCELLED + DISPUTED share the "đã đóng, không hoàn thành" bucket.
                // We surface them in a dedicated tab so customers can review the
                // refund banner / cancel reason without scrolling past completed
                // jobs. Sorted by updatedAt so the most recent cancellation is
                // on top — that's usually what the customer just came back for.
                val cancelled = all.filter {
                    it.status in listOf(
                        BookingStatus.CANCELLED,
                        BookingStatus.DISPUTED
                    )
                }.sortedByDescending { it.updatedAt }

                // Fetch reviews written by current customer
                val currentUser = authRepository.getCurrentUser()
                val reviewedBookingIds = if (currentUser != null) {
                    when (val reviewsResult = reviewRepository.getReviewsByCustomer(currentUser.id)) {
                        is Resource.Success -> reviewsResult.data.map { it.bookingId }.toSet()
                        else -> emptySet()
                    }
                } else {
                    emptySet()
                }

                _uiState.value = HistoryUiState.Success(
                    activeBookings = active,
                    completedBookings = completed,
                    cancelledBookings = cancelled,
                    reviewedBookingIds = reviewedBookingIds
                )
            }
            is Resource.Error -> {
                // Chỉ hiện error nếu chưa có data trước đó
                if (_uiState.value !is HistoryUiState.Success) {
                    _uiState.value = HistoryUiState.Error(result.message)
                }
                // Nếu đã có data, giữ nguyên data cũ (silent fail)
            }
            is Resource.Loading -> {}
        }
    }
}