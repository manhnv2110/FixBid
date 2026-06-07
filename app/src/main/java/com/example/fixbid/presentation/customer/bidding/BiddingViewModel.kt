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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BiddingUiState {
    object Loading : BiddingUiState()
    data class Success(val bids: List<Bid>) : BiddingUiState()
    data class Error(val message: String) : BiddingUiState()
    /**
     * The underlying booking is gone (deleted) or its status moved past the
     * bidding stage (cancelled, awaiting_payment, …). The screen should pop
     * back to a safer location instead of staying on a stale list.
     */
    data class BookingUnavailable(val message: String) : BiddingUiState()
}

sealed class BiddingEvent {
    data class Toast(val message: String) : BiddingEvent()
    data class NavigateToPayment(val bookingId: String) : BiddingEvent()
    data class NavigateToChat(
        val conversationId: String,
        val workerId: String,
        val workerName: String
    ) : BiddingEvent()

    /** Booking deleted/finished — screen owner should pop back. */
    data object BookingClosed : BiddingEvent()
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
        observeBidsRealtime()
        observeBookingLifecycle()
    }

    fun loadBids() {
        if (bookingId.isBlank()) {
            _uiState.value = BiddingUiState.Error("Không tìm thấy booking")
            return
        }
        viewModelScope.launch {
            _uiState.value = BiddingUiState.Loading
            // Verify the underlying booking still exists & is in a state where
            // bids matter — protects the screen from crashing on a deep-link
            // pointing to a deleted/cancelled booking.
            when (val bookingRes = bookingRepository.getBookingById(bookingId)) {
                is Resource.Error -> {
                    _uiState.value = BiddingUiState.BookingUnavailable(
                        bookingRes.message ?: "Yêu cầu này không còn tồn tại"
                    )
                    return@launch
                }
                is Resource.Success -> {
                    val booking = bookingRes.data
                    val statusName = booking.status.name
                    val isBidStage = statusName.equals("BIDDING", ignoreCase = true) ||
                        statusName.equals("PENDING", ignoreCase = true)
                    if (!isBidStage) {
                        _uiState.value = BiddingUiState.BookingUnavailable(
                            "Yêu cầu này không còn nhận báo giá."
                        )
                        return@launch
                    }
                }
                is Resource.Loading -> { /* no-op */ }
            }

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

    /**
     * Live-updates the bid list while the screen is open. Supabase realtime pushes
     * every insert/update on this booking's bids, so a new bid (or a status change
     * like accept/withdraw) shows up without the customer having to leave and
     * re-enter the screen. The initial [loadBids] still drives the first paint and
     * the error/retry state; this only swaps the list in once data starts flowing.
     */
    private fun observeBidsRealtime() {
        if (bookingId.isBlank()) return
        viewModelScope.launch {
            bidRepository.observeBidsForBooking(bookingId)
                .catch { /* realtime drop is non-fatal: the one-time load already populated the UI */ }
                .collect { bids ->
                    // Don't clobber the BookingUnavailable terminal state with
                    // a stale bid list cached on the realtime side.
                    if (_uiState.value !is BiddingUiState.BookingUnavailable) {
                        _uiState.value = BiddingUiState.Success(bids)
                    }
                }
        }
    }

    /**
     * Watches the booking row itself so we can react when the customer
     * cancels (or somebody server-side deletes) the booking while the screen
     * is open. Without this guard the realtime bid stream keeps pushing
     * empty lists and the screen sits there confusing the user.
     */
    private fun observeBookingLifecycle() {
        if (bookingId.isBlank()) return
        viewModelScope.launch {
            bookingRepository.observeBooking(bookingId)
                .catch { /* realtime drop is non-fatal */ }
                .collect { booking ->
                    if (booking == null) {
                        // Row vanished — push the user back so they don't
                        // act on stale bids.
                        _uiState.value = BiddingUiState.BookingUnavailable(
                            "Yêu cầu đã bị xoá."
                        )
                        _events.emit(BiddingEvent.BookingClosed)
                        return@collect
                    }
                    val statusName = booking.status.name
                    val stillBidStage = statusName.equals("BIDDING", ignoreCase = true) ||
                        statusName.equals("PENDING", ignoreCase = true)
                    if (!stillBidStage && _uiState.value is BiddingUiState.Success) {
                        // Booking advanced past bidding (someone accepted, or
                        // customer cancelled) — let the screen know so it can
                        // navigate away from the bid list.
                        _events.emit(BiddingEvent.BookingClosed)
                    }
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