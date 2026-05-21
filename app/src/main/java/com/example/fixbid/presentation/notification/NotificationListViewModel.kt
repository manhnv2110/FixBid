package com.example.fixbid.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NotificationListUiState {
    object Loading : NotificationListUiState()
    data class Success(val notifications: List<Notification>) : NotificationListUiState()
    data class Error(val message: String) : NotificationListUiState()
}

@HiltViewModel
class NotificationListViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationListUiState>(NotificationListUiState.Loading)
    val uiState: StateFlow<NotificationListUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.value = NotificationListUiState.Loading
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.value = NotificationListUiState.Error("Chưa đăng nhập")
                return@launch
            }
            currentUserId = user.id

            when (val result = notificationRepository.getNotifications(user.id)) {
                is Resource.Success -> {
                    _uiState.value = NotificationListUiState.Success(result.data)
                }
                is Resource.Error -> {
                    _uiState.value = NotificationListUiState.Error(result.message)
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
            // Reload notifications to update UI state
            val userId = currentUserId ?: authRepository.getCurrentUser()?.id
            if (userId != null) {
                val result = notificationRepository.getNotifications(userId)
                if (result is Resource.Success) {
                    _uiState.value = NotificationListUiState.Success(result.data)
                }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val userId = currentUserId ?: authRepository.getCurrentUser()?.id ?: return@launch
            _uiState.value = NotificationListUiState.Loading
            when (val result = notificationRepository.markAllAsRead(userId)) {
                is Resource.Success -> {
                    // Reload to update list
                    loadNotifications()
                }
                is Resource.Error -> {
                    _uiState.value = NotificationListUiState.Error(result.message)
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }
}
