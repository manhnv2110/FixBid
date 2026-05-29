package com.example.fixbid.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NotificationListUiState {
    object Loading : NotificationListUiState()
    data class Success(
        val notifications: List<Notification>,
        val isRefreshing: Boolean = false
    ) : NotificationListUiState()
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
    private var realtimeJob: Job? = null

    init {
        loadNotifications()
        observeRealtime()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            val existing = _uiState.value
            if (existing is NotificationListUiState.Success) {
                _uiState.value = existing.copy(isRefreshing = true)
            } else {
                _uiState.value = NotificationListUiState.Loading
            }

            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.value = NotificationListUiState.Error("Chưa đăng nhập")
                return@launch
            }
            currentUserId = user.id

            when (val result = notificationRepository.getNotifications(user.id)) {
                is Resource.Success ->
                    _uiState.value = NotificationListUiState.Success(result.data)
                is Resource.Error ->
                    _uiState.value = NotificationListUiState.Error(result.message)
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    /** Live-update the list whenever a new notification is inserted for this user. */
    private fun observeRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: return@launch
            currentUserId = user.id
            notificationRepository.observeNotifications(user.id).collect { notifications ->
                _uiState.value = NotificationListUiState.Success(notifications)
            }
        }
    }

    fun markAsRead(notificationId: String) {
        // Optimistically flip the flag so the UI updates immediately.
        val current = _uiState.value
        if (current is NotificationListUiState.Success) {
            _uiState.value = current.copy(
                notifications = current.notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
            )
        }
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }

    fun markAllAsRead() {
        val userId = currentUserId ?: return
        // Optimistic update.
        val current = _uiState.value
        if (current is NotificationListUiState.Success) {
            _uiState.value = current.copy(
                notifications = current.notifications.map { it.copy(isRead = true) }
            )
        }
        viewModelScope.launch {
            notificationRepository.markAllAsRead(userId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}
