package com.example.fixbid.presentation.worker.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.WorkerRepository
import com.example.fixbid.domain.usecase.worker.AcceptDirectBookingUseCase
import com.example.fixbid.domain.usecase.worker.DeclineDirectBookingUseCase
import com.example.fixbid.domain.usecase.worker.GetOpenJobRequestsUseCase
import com.example.fixbid.domain.usecase.worker.GetWorkerDashboardUseCase
import com.example.fixbid.domain.usecase.worker.ReleasePendingEscrowsUseCase
import com.example.fixbid.domain.usecase.worker.UpdateJobStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkerHomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val userName: String = "",
    val avatarUrl: String? = null,
    val isAvailable: Boolean = true,
    val profile: WorkerProfile? = null,
    val activeJobs: List<Booking> = emptyList(),       // IN_PROGRESS + PENDING_COMPLETION
    val pendingJobs: List<Booking> = emptyList(),      // CONFIRMED
    val completedJobs: List<Booking> = emptyList(),
    val cancelledJobs: List<Booking> = emptyList(),
    val openRequests: List<Booking> = emptyList(),     // BIDDING — preview ở dashboard
    /** DIRECT bookings assigned to this worker that need accept/decline. */
    val pendingDirectRequests: List<Booking> = emptyList(),
    val respondingDirectId: String? = null,            // worker đang xử lý đơn nào
    val completedCount: Int = 0,
    val totalEarnings: Double = 0.0,
    val monthlyEarnings: Double = 0.0,
    val errorMessage: String? = null,
    val isTogglingAvailability: Boolean = false
)

sealed interface WorkerHomeEvent {
    data class Toast(val message: String) : WorkerHomeEvent
}

@HiltViewModel
class WorkerHomeViewModel @Inject constructor(
    private val getDashboardUseCase: GetWorkerDashboardUseCase,
    private val getOpenJobRequestsUseCase: GetOpenJobRequestsUseCase,
    private val updateJobStatusUseCase: UpdateJobStatusUseCase,
    private val acceptDirectBookingUseCase: AcceptDirectBookingUseCase,
    private val declineDirectBookingUseCase: DeclineDirectBookingUseCase,
    private val releasePendingEscrows: ReleasePendingEscrowsUseCase,
    private val workerRepository: WorkerRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerHomeUiState())
    val uiState: StateFlow<WorkerHomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<WorkerHomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard(forceShowLoading: Boolean = false) {
        viewModelScope.launch {
            val hasData = _uiState.value.profile != null
            if (forceShowLoading || !hasData) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            // Belt-and-braces: clear any escrows that got stuck because a
            // previous customer confirm-completion could not finish the
            // release step. Idempotent and quiet.
            val recovered = runCatching { releasePendingEscrows() }.getOrDefault(0)

            // Tải thông tin cá nhân và dữ liệu dashboard song song để tối ưu hiệu năng
            val userDeferred = async { authRepository.getCurrentUser() }
            val dashboardDeferred = async { getDashboardUseCase() }

            // Cập nhật thông tin cá nhân ngay khi tải xong (thường cực nhanh từ local cache) để tránh delay UI
            val user = userDeferred.await()
            val userName = user?.fullName ?: ""
            val avatarUrl = user?.avatarUrl
            _uiState.update {
                it.copy(
                    userName = userName,
                    avatarUrl = avatarUrl
                )
            }

            when (val result = dashboardDeferred.await()) {
                is Resource.Success -> {
                    val data = result.data

                    // Lấy thêm preview của open requests cho dashboard
                    val openRequests = (getOpenJobRequestsUseCase(applySkillsFilter = true) as? Resource.Success)
                        ?.data?.take(5) ?: emptyList()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userName = userName,
                            avatarUrl = avatarUrl,
                            isAvailable = data.profile?.isAvailable ?: true,
                            profile = data.profile,
                            activeJobs = data.activeJobs,
                            pendingJobs = data.pendingJobs,
                            completedJobs = data.completedJobs,
                            cancelledJobs = data.cancelledJobs,
                            openRequests = openRequests,
                            pendingDirectRequests = data.pendingDirectRequests,
                            completedCount = data.completedCount,
                            totalEarnings = data.totalEarnings,
                            monthlyEarnings = data.monthlyEarnings,
                            errorMessage = null
                        )
                    }
                    // Surface a soft toast / event when stuck payouts are
                    // released — handy for both the worker and for QA.
                    if (recovered > 0) {
                        android.util.Log.i(
                            "WorkerHome",
                            "Recovered $recovered stuck escrow release(s)"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userName = userName,
                            avatarUrl = avatarUrl,
                            errorMessage = if (hasData) null else result.message
                        )
                    }
                    if (hasData) {
                        _events.trySend(WorkerHomeEvent.Toast(result.message ?: "Không thể làm mới dữ liệu"))
                    }
                }
                is Resource.Loading -> { /* no-op */ }
            }
            // Always clear the refresh spinner once we've completed a load
            // cycle, even on failure, so pull-to-refresh doesn't stick on.
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /** Pull-to-refresh entry-point: shows a refresh spinner without flashing the full-screen loader. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        loadDashboard()
    }

    fun toggleAvailability() {
        val current = _uiState.value.isAvailable
        viewModelScope.launch {
            _uiState.update { it.copy(isTogglingAvailability = true) }
            when (workerRepository.setAvailability(!current)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isAvailable = !current,
                            isTogglingAvailability = false
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isTogglingAvailability = false) }
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    fun startJob(bookingId: String) {
        viewModelScope.launch {
            when (val result = updateJobStatusUseCase(bookingId, BookingStatus.IN_PROGRESS)) {
                is Resource.Success -> loadDashboard()
                is Resource.Error -> _events.trySend(WorkerHomeEvent.Toast(result.message))
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    fun completeJob(bookingId: String) {
        viewModelScope.launch {
            when (val result = updateJobStatusUseCase(bookingId, BookingStatus.PENDING_COMPLETION)) {
                is Resource.Success -> loadDashboard()
                is Resource.Error -> _events.trySend(WorkerHomeEvent.Toast(result.message))
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * Accept a direct booking the customer assigned to me. Backend transitions
     * the booking to AWAITING_PAYMENT and pushes a notification to the customer.
     * The dashboard reloads so the new "đang chờ thanh toán" job moves out of
     * pendingDirectRequests and into the appropriate bucket.
     */
    fun acceptDirectBooking(bookingId: String) {
        if (_uiState.value.respondingDirectId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(respondingDirectId = bookingId) }
            when (val result = acceptDirectBookingUseCase(bookingId)) {
                is Resource.Success -> {
                    _events.trySend(WorkerHomeEvent.Toast("Đã nhận đơn — chờ khách thanh toán"))
                    loadDashboard()
                }
                is Resource.Error -> {
                    _events.trySend(WorkerHomeEvent.Toast(result.message))
                    _uiState.update { it.copy(respondingDirectId = null) }
                }
                is Resource.Loading -> { /* no-op */ }
            }
            _uiState.update { it.copy(respondingDirectId = null) }
        }
    }

    /** Decline a direct booking with an optional reason. Reloads the dashboard on success. */
    fun declineDirectBooking(bookingId: String, reason: String) {
        if (_uiState.value.respondingDirectId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(respondingDirectId = bookingId) }
            when (val result = declineDirectBookingUseCase(bookingId, reason)) {
                is Resource.Success -> {
                    _events.trySend(WorkerHomeEvent.Toast("Đã từ chối đơn"))
                    loadDashboard()
                }
                is Resource.Error -> {
                    _events.trySend(WorkerHomeEvent.Toast(result.message))
                    _uiState.update { it.copy(respondingDirectId = null) }
                }
                is Resource.Loading -> { /* no-op */ }
            }
            _uiState.update { it.copy(respondingDirectId = null) }
        }
    }
}
