package com.example.fixbid.presentation.worker.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.usecase.worker.GetOpenJobRequestsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class JobRequestSortBy(val displayName: String) {
    NEWEST("Mới nhất"),
    BUDGET_HIGH("Ngân sách cao"),
    BUDGET_LOW("Ngân sách thấp"),
    SOONEST("Hẹn sớm nhất")
}

data class JobRequestsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val jobs: List<Booking> = emptyList(),
    val selectedCategory: ServiceCategory? = null,
    val onlyMySkills: Boolean = true,
    val sortBy: JobRequestSortBy = JobRequestSortBy.NEWEST,
    val errorMessage: String? = null
)

@HiltViewModel
class JobRequestsViewModel @Inject constructor(
    private val getOpenJobRequestsUseCase: GetOpenJobRequestsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(JobRequestsUiState())
    val uiState: StateFlow<JobRequestsUiState> = _uiState.asStateFlow()

    init {
        load(initial = true)
    }

    fun setCategoryFilter(category: ServiceCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
        load()
    }

    fun toggleSkillsFilter() {
        _uiState.update { it.copy(onlyMySkills = !it.onlyMySkills) }
        load()
    }

    fun setSortBy(sort: JobRequestSortBy) {
        val current = _uiState.value
        _uiState.update {
            it.copy(
                sortBy = sort,
                jobs = applySort(current.jobs, sort)
            )
        }
    }

    fun refresh() = load(refresh = true)

    private fun load(initial: Boolean = false, refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initial,
                    isRefreshing = refresh,
                    errorMessage = null
                )
            }
            val state = _uiState.value
            when (val result = getOpenJobRequestsUseCase(
                categoryFilter = state.selectedCategory,
                applySkillsFilter = state.onlyMySkills
            )) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        jobs = applySort(result.data, state.sortBy)
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    private fun applySort(jobs: List<Booking>, sortBy: JobRequestSortBy): List<Booking> = when (sortBy) {
        JobRequestSortBy.NEWEST -> jobs.sortedByDescending { it.createdAt }
        JobRequestSortBy.BUDGET_HIGH -> jobs.sortedByDescending { it.agreedPrice ?: 0.0 }
        JobRequestSortBy.BUDGET_LOW -> jobs.sortedBy { it.agreedPrice ?: Double.MAX_VALUE }
        JobRequestSortBy.SOONEST -> jobs.sortedBy { it.scheduledAt }
    }
}
