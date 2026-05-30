package com.example.fixbid.presentation.customer.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.usecase.customer.DiscoverWorkersUseCase
import com.example.fixbid.domain.usecase.customer.DiscoveredWorker
import com.example.fixbid.domain.usecase.customer.WorkerSortBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoverWorkersUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val workers: List<DiscoveredWorker> = emptyList(),
    val query: String = "",
    val selectedCategory: ServiceCategory? = null,
    val sortBy: WorkerSortBy = WorkerSortBy.RATING,
    val errorMessage: String? = null
) {
    val visibleWorkers: List<DiscoveredWorker>
        get() = if (query.isBlank()) workers
        else workers.filter { it.displayName.contains(query.trim(), ignoreCase = true) }
}

@HiltViewModel
class DiscoverWorkersViewModel @Inject constructor(
    private val discoverWorkersUseCase: DiscoverWorkersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverWorkersUiState())
    val uiState: StateFlow<DiscoverWorkersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !refresh, isRefreshing = refresh, errorMessage = null) }
            val state = _uiState.value
            when (val result = discoverWorkersUseCase(state.selectedCategory, state.sortBy)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, workers = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun refresh() = load(refresh = true)

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun setCategory(category: ServiceCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
        load()
    }

    fun setSortBy(sortBy: WorkerSortBy) {
        _uiState.update { it.copy(sortBy = sortBy) }
        load()
    }
}
