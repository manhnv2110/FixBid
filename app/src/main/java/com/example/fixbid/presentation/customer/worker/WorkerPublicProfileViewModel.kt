package com.example.fixbid.presentation.customer.worker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.usecase.customer.GetWorkerPublicProfileUseCase
import com.example.fixbid.domain.usecase.customer.WorkerPublicProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkerPublicProfileUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val data: WorkerPublicProfile? = null,
    val errorMessage: String? = null
) {
    /** rating value (1..5) → count, used for distribution bars. */
    val distribution: Map<Int, Int>
        get() = (1..5).associateWith { star -> data?.reviews?.count { it.rating == star } ?: 0 }
}

@HiltViewModel
class WorkerPublicProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWorkerPublicProfileUseCase: GetWorkerPublicProfileUseCase
) : ViewModel() {

    private val workerId: String = savedStateHandle.get<String>("workerId") ?: ""

    private val _uiState = MutableStateFlow(WorkerPublicProfileUiState())
    val uiState: StateFlow<WorkerPublicProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        if (workerId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Không tìm thấy thợ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !refresh, isRefreshing = refresh, errorMessage = null) }
            when (val result = getWorkerPublicProfileUseCase(workerId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, data = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun refresh() = load(refresh = true)
}
