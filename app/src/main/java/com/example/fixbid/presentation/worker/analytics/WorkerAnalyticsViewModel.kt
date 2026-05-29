package com.example.fixbid.presentation.worker.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.usecase.worker.GetWorkerAnalyticsUseCase
import com.example.fixbid.domain.usecase.worker.WorkerAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkerAnalyticsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val analytics: WorkerAnalytics? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class WorkerAnalyticsViewModel @Inject constructor(
    private val getWorkerAnalyticsUseCase: GetWorkerAnalyticsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerAnalyticsUiState())
    val uiState: StateFlow<WorkerAnalyticsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !refresh, isRefreshing = refresh, errorMessage = null) }
            when (val result = getWorkerAnalyticsUseCase()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, analytics = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    fun refresh() = load(refresh = true)
}
