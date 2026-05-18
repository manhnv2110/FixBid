package com.example.fixbid.presentation.worker.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.WorkerRepository
import com.example.fixbid.domain.usecase.worker.GetWorkerDashboardUseCase
import com.example.fixbid.domain.usecase.worker.UpdateJobStatusUseCase
import com.example.fixbid.domain.usecase.worker.WorkerDashboardData
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
    val activeJobs: List<Booking> = emptyList(),
    val pendingJobs: List<Booking> = emptyList(),
    val completedCount: Int = 0,
    val totalEarnings: Double = 0.0,
    val monthlyEarnings: Double = 0.0,
    val errorMessage: String? = null,
    val isTogglingAvailability: Boolean = false
)

@HiltViewModel
class WorkerHomeViewModel @Inject constructor(
    private val getDashboardUseCase: GetWorkerDashboardUseCase,
    private val updateJobStatusUseCase: UpdateJobStatusUseCase,
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

            val user = authRepository.getCurrentUser()
            val userName = user?.fullName ?: ""

            when (val result = getDashboardUseCase()) {
                is Resource.Success -> {
                    val data = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userName = userName,
                            isAvailable = data.profile?.isAvailable ?: true,
                            profile = data.profile,
                            activeJobs = data.activeJobs,
                            pendingJobs = data.pendingJobs,
                            completedCount = data.completedCount,
                            totalEarnings = data.totalEarnings,
                            monthlyEarnings = data.monthlyEarnings
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
                else -> { /* TODO: show error */ }
            }
        }
    }

    fun completeJob(bookingId: String) {
        viewModelScope.launch {
            when (updateJobStatusUseCase(bookingId, BookingStatus.COMPLETED)) {
                is Resource.Success -> loadDashboard()
                else -> { /* TODO: show error */ }
            }
        }
    }
}
