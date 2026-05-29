package com.example.fixbid.presentation.worker.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.data.location.GeocoderRepository
import com.example.fixbid.data.location.LocationRepository
import com.example.fixbid.data.location.RoutingService
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

data class WorkerNavigationUiState(
    val isLoading: Boolean = true,
    val booking: Booking? = null,
    val customerLocation: GeoPoint? = null,
    val workerLocation: GeoPoint? = null,
    val routePoints: List<GeoPoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val errorMessage: String? = null,
    val needsLocationPermission: Boolean = false,
    val isResolvingAddress: Boolean = false,
    val addressUnresolved: Boolean = false
)

@HiltViewModel
class WorkerNavigationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingRepository: BookingRepository,
    private val locationRepository: LocationRepository,
    private val geocoderRepository: GeocoderRepository,
    private val routingService: RoutingService,
    private val authRepository: AuthRepository,
    private val sendNotification: SendNotificationUseCase
) : ViewModel() {

    private val bookingId: String = savedStateHandle.get<String>("bookingId") ?: ""

    private val _uiState = MutableStateFlow(WorkerNavigationUiState())
    val uiState: StateFlow<WorkerNavigationUiState> = _uiState.asStateFlow()

    /** Đảm bảo chỉ gửi thông báo "thợ đang đến" một lần cho mỗi phiên chỉ đường. */
    private var onTheWayNotified = false

    init {
        load()
    }

    /**
     * Báo cho khách hàng biết thợ đang trên đường tới. Được gọi khi thợ mở màn
     * hình chỉ đường. Gửi tối đa một lần / phiên, lỗi không ảnh hưởng điều hướng.
     */
    fun notifyCustomerOnTheWay() {
        if (onTheWayNotified) return
        val booking = _uiState.value.booking ?: return
        val customerId = booking.customerId.takeIf { it.isNotBlank() } ?: return
        onTheWayNotified = true
        viewModelScope.launch {
            val workerName = authRepository.getCurrentUser()?.fullName ?: "Thợ dịch vụ"
            sendNotification(
                NotificationContentFactory.workerOnTheWayForCustomer(
                    customerId = customerId,
                    bookingId = booking.id,
                    workerName = workerName
                )
            )
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = bookingRepository.getBookingById(bookingId)) {
                is Resource.Success -> {
                    val booking = result.data
                    _uiState.update { it.copy(booking = booking) }
                    val customerPoint = resolveCustomerLocation(booking)
                    if (customerPoint == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                addressUnresolved = true,
                                errorMessage = "Không xác định được vị trí khách hàng. Bạn vẫn có thể mở bằng app bản đồ."
                            )
                        }
                        return@launch
                    }
                    _uiState.update {
                        it.copy(customerLocation = customerPoint, isLoading = false)
                    }
                    refreshWorkerLocationAndRoute()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * Re-fetches the worker's GPS coordinates and asks OSRM for an updated route. Safe
     * to call repeatedly (e.g. from the "Recenter" button).
     */
    fun refreshWorkerLocationAndRoute() {
        if (!locationRepository.hasFineLocationPermission()) {
            _uiState.update { it.copy(needsLocationPermission = true) }
            return
        }
        viewModelScope.launch {
            val customer = _uiState.value.customerLocation ?: return@launch
            val location = locationRepository.getCurrentLocation()
            if (location == null) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Không lấy được vị trí của bạn. Hãy bật GPS và thử lại.",
                        needsLocationPermission = false
                    )
                }
                return@launch
            }
            val workerPoint = GeoPoint(location.latitude, location.longitude)
            _uiState.update {
                it.copy(
                    workerLocation = workerPoint,
                    needsLocationPermission = false,
                    errorMessage = null
                )
            }

            val route = routingService.fetchRoute(workerPoint, customer)
            if (route != null) {
                _uiState.update {
                    it.copy(
                        routePoints = route.points,
                        distanceMeters = route.distanceMeters,
                        durationSeconds = route.durationSeconds
                    )
                }
                // Worker is actively navigating → let the customer know once.
                notifyCustomerOnTheWay()
            } else {
                // Routing failed but we still have both endpoints — draw a straight
                // line so the worker at least sees the bearing.
                _uiState.update {
                    it.copy(
                        routePoints = listOf(workerPoint, customer),
                        distanceMeters = workerPoint.distanceToAsDouble(customer),
                        durationSeconds = 0.0
                    )
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _uiState.update { it.copy(needsLocationPermission = false) }
            refreshWorkerLocationAndRoute()
        } else {
            _uiState.update {
                it.copy(
                    needsLocationPermission = true,
                    errorMessage = "Cần quyền truy cập vị trí để hiển thị chỉ đường"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun resolveCustomerLocation(booking: Booking): GeoPoint? {
        val lat = booking.latitude
        val lng = booking.longitude
        if (lat != null && lng != null) {
            return GeoPoint(lat, lng)
        }
        // Fallback: geocode the human-readable address. Common in production for any
        // booking that was created before lat/lng capture was wired in (or when the
        // customer typed a free-form address).
        _uiState.update { it.copy(isResolvingAddress = true) }
        val resolved = geocoderRepository.resolveAddress(booking.address)
        _uiState.update { it.copy(isResolvingAddress = false) }
        return resolved?.let { GeoPoint(it.latitude, it.longitude) }
    }
}
