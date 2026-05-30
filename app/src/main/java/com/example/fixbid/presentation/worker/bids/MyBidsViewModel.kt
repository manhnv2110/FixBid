package com.example.fixbid.presentation.worker.bids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.BidStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.usecase.worker.GetMyBidsUseCase
import com.example.fixbid.domain.usecase.worker.MyBid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MyBidsFilter(val label: String) {
    ALL("Tất cả"),
    PENDING("Đang chờ"),
    ACCEPTED("Được chọn"),
    REJECTED("Không chọn")
}

data class MyBidsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val allBids: List<MyBid> = emptyList(),
    val filter: MyBidsFilter = MyBidsFilter.ALL,
    val errorMessage: String? = null,
    val withdrawingId: String? = null
) {
    val filteredBids: List<MyBid>
        get() = when (filter) {
            MyBidsFilter.ALL -> allBids
            MyBidsFilter.PENDING -> allBids.filter { it.bid.status == BidStatus.PENDING }
            MyBidsFilter.ACCEPTED -> allBids.filter { it.bid.status == BidStatus.ACCEPTED }
            MyBidsFilter.REJECTED -> allBids.filter {
                it.bid.status == BidStatus.REJECTED || it.bid.status == BidStatus.WITHDRAWN
            }
        }

    val pendingCount: Int get() = allBids.count { it.bid.status == BidStatus.PENDING }
    val acceptedCount: Int get() = allBids.count { it.bid.status == BidStatus.ACCEPTED }
}

sealed interface MyBidsEvent {
    data class Toast(val message: String) : MyBidsEvent
}

@HiltViewModel
class MyBidsViewModel @Inject constructor(
    private val getMyBidsUseCase: GetMyBidsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyBidsUiState())
    val uiState: StateFlow<MyBidsUiState> = _uiState.asStateFlow()

    private val _events = Channel<MyBidsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !refresh, isRefreshing = refresh, errorMessage = null) }
            when (val result = getMyBidsUseCase()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, allBids = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun refresh() = load(refresh = true)

    fun setFilter(filter: MyBidsFilter) = _uiState.update { it.copy(filter = filter) }

    fun withdraw(bidId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(withdrawingId = bidId) }
            when (val result = getMyBidsUseCase.withdraw(bidId)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(withdrawingId = null) }
                    _events.send(MyBidsEvent.Toast("Đã rút báo giá"))
                    load(refresh = true)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(withdrawingId = null) }
                    _events.send(MyBidsEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }
}
