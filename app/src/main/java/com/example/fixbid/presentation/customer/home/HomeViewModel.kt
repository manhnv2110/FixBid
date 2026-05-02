package com.example.fixbid.presentation.customer.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.data.dto.NotificationDto
import com.example.fixbid.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NotificationUIState {
    object Loading : NotificationUIState()
    data class Success(val notifications: List<NotificationDto>) : NotificationUIState()
    data class Error(val message: String) : NotificationUIState()
}

class HomeViewModel(
    private val notificationRepo: NotificationRepository = NotificationRepository()
) : ViewModel() {
    private val _notificationState = MutableStateFlow<NotificationUIState>(NotificationUIState.Loading)
    val notificationState: StateFlow<NotificationUIState> = _notificationState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            Log.d("HomeViewModel", ">>> Bắt đầu load notifications...")
            _notificationState.value = NotificationUIState.Loading
            notificationRepo.getMyNotifications()
                .onSuccess { list ->
                    Log.d("HomeViewModel", ">>> Thành công! Số lượng: ${list.size}")
                    list.forEach { Log.d("HomeViewModel", ">>> Item: $it") }
                    _notificationState.value = NotificationUIState.Success(list)
                }
                .onFailure { error ->
                    Log.e("HomeViewModel", ">>> Lỗi: ${error.message}")
                    Log.e("HomeViewModel", ">>> Chi tiết: ${error.stackTraceToString()}")
                    _notificationState.value = NotificationUIState.Error(error.message ?: "Không thể tải thông báo")
                }
        }
    }
}