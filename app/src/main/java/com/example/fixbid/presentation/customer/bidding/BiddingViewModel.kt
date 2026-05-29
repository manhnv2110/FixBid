package com.example.fixbid.presentation.customer.bidding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BidRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.ChatRepository
import com.example.fixbid.domain.repository.WorkerRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BiddingUiState {
    object Loading : BiddingUiState()
    data class Success(val bids: List<Bid>) : BiddingUiState()
    data class Error(val message: String) : BiddingUiState()
}

sealed class BiddingEvent {
    data class Toast(val message: String) : BiddingEvent()
    data class NavigateToPayment(val bookingId: String) : BiddingEvent()
    data class NavigateToChat(
        val conversationId: String,
        val workerId: String,
        val workerName: String
    ) : BiddingEvent()
}

@HiltViewModel
class BiddingViewModel @Inject constructor(
    private val bidRepository: BidRepository,
    private val bookingRepository: BookingRepository,
    private val workerRepository: WorkerRepository,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val sendNotification: SendNotificationUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookingId: String = savedStateHandle["bookingId"] ?: ""

    private val _uiState = MutableStateFlow<BiddingUiState>(BiddingUiState.Loading)
    val uiState: StateFlow<BiddingUiState> = _uiState.asStateFlow()

    private val _selectedWorkerProfile = MutableStateFlow<WorkerProfile?>(null)
    val selectedWorkerProfile: StateFlow<WorkerProfile?> = _selectedWorkerProfile.asStateFlow()

    private val _isLoadingProfile = MutableStateFlow(false)
    val isLoadingProfile: StateFlow<Boolean> = _isLoadingProfile.asStateFlow()

    private val _events = MutableSharedFlow<BiddingEvent>()
    val events: SharedFlow<BiddingEvent> = _events.asSharedFlow()

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

    fun loadWorkerProfile(workerId: String) {
        viewModelScope.launch {
            _isLoadingProfile.value = true
            when (val result = workerRepository.getWorkerById(workerId)) {
                is Resource.Success -> {
                    _selectedWorkerProfile.value = result.data
                }
                is Resource.Error -> {
                    _selectedWorkerProfile.value = null
                }
                is Resource.Loading -> { /* no-op */ }
            }
            _isLoadingProfile.value = false
        }
    }

    fun clearSelectedWorkerProfile() {
        _selectedWorkerProfile.value = null
    }

    /**
     * Khách chọn thợ (accept bid).
     * Sau khi accept thành công:
     * - DB trigger (handle_bid_accepted) tự set booking = awaiting_payment,
     *   gán worker_id, agreed_price, và reject các bid khác.
     * - Navigate sang màn hình thanh toán.
     * - Booking chỉ chuyển sang CONFIRMED sau khi thanh toán VNPay thành công
     *   (trong ProcessVNPayReturnUseCase).
     */
    fun acceptBid(bidId: String) {
        viewModelScope.launch {
            when (val result = bidRepository.acceptBid(bidId)) {
                is Resource.Success -> {
                    // Notify the chosen worker their bid was accepted. Non-fatal.
                    val acceptedBid = result.data
                    val categoryName = (bookingRepository.getBookingById(bookingId) as? Resource.Success)
                        ?.data?.category?.displayName ?: "công việc"
                    sendNotification(
                        NotificationContentFactory.bidAcceptedForWorker(
                            workerId = acceptedBid.workerId,
                            bookingId = bookingId,
                            categoryName = categoryName
                        )
                    )
                    _events.emit(BiddingEvent.Toast("Đã chọn thợ! Vui lòng tiến hành thanh toán."))
                    _events.emit(BiddingEvent.NavigateToPayment(bookingId))
                }
                is Resource.Error -> {
                    _events.emit(BiddingEvent.Toast(result.message))
                    loadBids()
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * Tạo hoặc mở cuộc trò chuyện với thợ, sau đó navigate sang ChatScreen.
     */
    fun openChatWithWorker(workerId: String, workerName: String) {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser()
            val customerId = currentUser?.id ?: run {
                _events.emit(BiddingEvent.Toast("Bạn cần đăng nhập để nhắn tin"))
                return@launch
            }
            when (val result = chatRepository.getOrCreateConversation(
                customerId = customerId,
                workerId   = workerId,
                bookingId  = bookingId.ifBlank { null }
            )) {
                is Resource.Success -> {
                    _events.emit(
                        BiddingEvent.NavigateToChat(
                            conversationId = result.data.id,
                            workerId       = workerId,
                            workerName     = workerName
                        )
                    )
                }
                is Resource.Error -> {
                    _events.emit(BiddingEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }
}