package com.example.fixbid.presentation.worker.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.WorkerRepository
import com.example.fixbid.domain.usecase.worker.GetOpenJobRequestsUseCase
import com.example.fixbid.domain.usecase.worker.GetWorkerDashboardUseCase
import com.example.fixbid.domain.usecase.worker.ReleasePendingEscrowsUseCase
import com.example.fixbid.domain.usecase.worker.UpdateJobStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkerHomeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val isAvailable: Boolean = true,
    val profile: WorkerProfile? = null,
    val activeJobs: List<Booking> = emptyList(),       // IN_PROGRESS + PENDING_COMPLETION
    val pendingJobs: List<Booking> = emptyList(),      // CONFIRMED
    val completedJobs: List<Booking> = emptyList(),
    val openRequests: List<Booking> = emptyList(),     // BIDDING — preview ở dashboard
    val completedCount: Int = 0,
    val totalEarnings: Double = 0.0,
    val monthlyEarnings: Double = 0.0,
    val errorMessage: String? = null,
    val isTogglingAvailability: Boolean = false
)

@HiltViewModel
class WorkerHomeViewModel @Inject constructor(
    private val getDashboardUseCase: GetWorkerDashboardUseCase,
    private val getOpenJobRequestsUseCase: GetOpenJobRequestsUseCase,
    private val updateJobStatusUseCase: UpdateJobStatusUseCase,
    private val releasePendingEscrows: ReleasePendingEscrowsUseCase,
    private val workerRepository: WorkerRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerHomeUiState())
    val uiState: StateFlow<WorkerHomeUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Belt-and-braces: clear any escrows that got stuck because a
            // previous customer confirm-completion could not finish the
            // release step. Idempotent and quiet.
            val recovered = runCatching { releasePendingEscrows() }.getOrDefault(0)

            val user = authRepository.getCurrentUser()
            val userName = user?.fullName ?: ""

            when (val result = getDashboardUseCase()) {
                is Resource.Success -> {
                    val data = result.data

                    // Lấy thêm preview của open requests cho dashboard
                    val openRequests = (getOpenJobRequestsUseCase(applySkillsFilter = true) as? Resource.Success)
                        ?.data?.take(5) ?: emptyList()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userName = userName,
                            isAvailable = data.profile?.isAvailable ?: true,
                            profile = data.profile,
                            activeJobs = data.activeJobs,
                            pendingJobs = data.pendingJobs,
                            completedJobs = data.completedJobs,
                            openRequests = openRequests,
                            completedCount = data.completedCount,
                            totalEarnings = data.totalEarnings,
                            monthlyEarnings = data.monthlyEarnings
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
                            errorMessage = result.message
                        )
                    }
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
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
            when (updateJobStatusUseCase(bookingId, BookingStatus.IN_PROGRESS)) {
                is Resource.Success -> loadDashboard()
                else -> { /* TODO: surface error via event */ }
            }
        }
    }

    fun completeJob(bookingId: String) {
        viewModelScope.launch {
            when (updateJobStatusUseCase(bookingId, BookingStatus.PENDING_COMPLETION)) {
                is Resource.Success -> loadDashboard()
                else -> { /* TODO: surface error via event */ }
            }
        }
    }
}
