package com.example.fixbid.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.data.local.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationSettingsState(
    val enabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationSettingsState())
    val state: StateFlow<NotificationSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.notificationsEnabled,
                preferences.notificationSoundEnabled,
                preferences.notificationVibrateEnabled
            ) { enabled, sound, vibrate ->
                NotificationSettingsState(enabled, sound, vibrate)
            }.collect { _state.value = it }
        }
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setNotificationsEnabled(value) }
    }

    fun setSoundEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setNotificationSoundEnabled(value) }
    }

    fun setVibrateEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setNotificationVibrateEnabled(value) }
    }
}
