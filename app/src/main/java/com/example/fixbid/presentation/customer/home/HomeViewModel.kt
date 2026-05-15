package com.example.fixbid.presentation.customer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.usecase.shared.GetNotificationsUseCase
import com.example.fixbid.core.utils.ServiceCategoryMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NotificationUiState {
    object Loading : NotificationUiState()
    data class Success(val notifications: List<Notification>) : NotificationUiState()
    data class Error(val message: String) : NotificationUiState()
}

data class HomeUiState(
    val categories: List<ServiceCategory> = ServiceCategoryMapper.homeCategories,
    val searchQuery: String = "",
    val notificationState: NotificationUiState = NotificationUiState.Loading
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getNotificationsUseCase: GetNotificationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                notificationState = NotificationUiState.Loading
            )
            when (val result = getNotificationsUseCase()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        notificationState = NotificationUiState.Success(result.data)
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        notificationState = NotificationUiState.Error(result.message)
                    )
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }
}
