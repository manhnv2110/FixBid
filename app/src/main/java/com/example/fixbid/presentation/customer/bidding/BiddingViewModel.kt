package com.example.fixbid.presentation.customer.bidding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BidRepository
import com.example.fixbid.domain.repository.BookingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BiddingUiState {
    object Loading : BiddingUiState()
    data class Success(val bids: List<Bid>) : BiddingUiState()
    data class Error(val message: String) : BiddingUiState()
}

@HiltViewModel
class BiddingViewModel @Inject constructor(
    private val bidRepository: BidRepository,
    private val bookingRepository: BookingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookingId: String = savedStateHandle["bookingId"] ?: ""

    private val _uiState = MutableStateFlow<BiddingUiState>(BiddingUiState.Loading)
    val uiState: StateFlow<BiddingUiState> = _uiState.asStateFlow()

    init {
        loadBids()
    }

    fun loadBids() {
        if (bookingId.isBlank()) {
            _uiState.value = BiddingUiState.Error("Không tìm thấy booking")
            return
        }
        viewModelScope.launch {
            _uiState.value = BiddingUiState.Loading
            when (val result = bidRepository.getBidsForBooking(bookingId)) {
                is Resource.Success -> {
                    _uiState.value = BiddingUiState.Success(result.data)
                }
                is Resource.Error -> {
                    _uiState.value = BiddingUiState.Error(result.message)
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    fun acceptBid(bidId: String) {
        viewModelScope.launch {
            when (val result = bidRepository.acceptBid(bidId)) {
                is Resource.Success -> {
                    // Reload bids to reflect the change
                    loadBids()
                }
                is Resource.Error -> {
                    // Could show a toast/snackbar, for now just reload
                    loadBids()
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }
}
