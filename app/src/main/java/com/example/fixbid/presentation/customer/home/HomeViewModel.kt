package com.example.fixbid.presentation.customer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.usecase.shared.GetNotificationsUseCase
import com.example.fixbid.core.utils.ServiceCategoryMapper
import com.example.fixbid.data.location.LocationRepository
import com.example.fixbid.data.location.GeocoderRepository
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
    val notificationState: NotificationUiState = NotificationUiState.Loading,
    val cityName: String = "Hà Nội",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val dismissedNotificationIds: Set<String> = emptySet()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getNotificationsUseCase: GetNotificationsUseCase,
    private val locationRepository: LocationRepository,
    private val geocoderRepository: GeocoderRepository
) : ViewModel() {

    val locator: LocationRepository get() = locationRepository
    val geocoder: GeocoderRepository get() = geocoderRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
        fetchCurrentLocation()
    }

    fun fetchCurrentLocation() {
        viewModelScope.launch {
            val loc = locationRepository.getCurrentLocation()
            if (loc != null) {
                val city = geocoderRepository.getCityName(loc.latitude, loc.longitude)
                _uiState.value = _uiState.value.copy(
                    cityName = city ?: "Hà Nội",
                    latitude = loc.latitude,
                    longitude = loc.longitude
                )
            }
        }
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val city = geocoderRepository.getCityName(latitude, longitude)
            _uiState.value = _uiState.value.copy(
                cityName = city ?: "Hà Nội",
                latitude = latitude,
                longitude = longitude
            )
        }
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

    fun dismissNotifications(ids: List<String>) {
        _uiState.value = _uiState.value.copy(
            dismissedNotificationIds = _uiState.value.dismissedNotificationIds + ids
        )
    }
}
