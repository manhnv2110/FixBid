package com.example.fixbid.presentation.customer.booking

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.data.location.GeocoderRepository
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.BookingType
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class BookingUiState {
    object Idle : BookingUiState()
    object Loading : BookingUiState()
    data class Success(val bookingId: String) : BookingUiState()
    data class Error(val message: String) : BookingUiState()
}

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository,
    private val geocoderRepository: GeocoderRepository,
    private val locationRepository: com.example.fixbid.data.location.LocationRepository
) : ViewModel() {

    // Exposed for the address picker sheet (UI-only consumer).
    val geocoder: GeocoderRepository get() = geocoderRepository
    val locator: com.example.fixbid.data.location.LocationRepository get() = locationRepository

    private val _uiState = MutableStateFlow<BookingUiState>(BookingUiState.Idle)
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    private val _initialFullName = MutableStateFlow("")
    val initialFullName: StateFlow<String> = _initialFullName.asStateFlow()

    private val _initialPhone = MutableStateFlow("")
    val initialPhone: StateFlow<String> = _initialPhone.asStateFlow()

    // Ảnh mô tả công việc được chọn bởi người dùng
    private val _descriptionImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val descriptionImageUris: StateFlow<List<Uri>> = _descriptionImageUris.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser()?.let { user ->
                _initialFullName.value = user.fullName
                _initialPhone.value = user.phoneNumber ?: ""
            }
        }
    }

    fun onDescriptionImagesSelected(uris: List<Uri>) {
        val current = _descriptionImageUris.value.toMutableList()
        val remaining = 5 - current.size
        current.addAll(uris.take(remaining))
        _descriptionImageUris.value = current
    }

    fun removeDescriptionImage(uri: Uri) {
        _descriptionImageUris.value = _descriptionImageUris.value.filter { it != uri }
    }

    fun createBooking(
        category: ServiceCategory,
        description: String,
        address: String,
        phoneNumber: String,
        fullName: String,
        notes: String,
        scheduledAtMillis: Long,
        latitude: Double? = null,
        longitude: Double? = null,
        imageResolver: (Uri) -> ByteArray?
    ) {
        viewModelScope.launch {
            _uiState.value = BookingUiState.Loading
            val currentUser = authRepository.getCurrentUser()

            if (currentUser == null) {
                _uiState.value = BookingUiState.Error("Vui lòng đăng nhập để tiếp tục")
                return@launch
            }

            // If the customer typed a free-form address but didn't pick a point on the
            // map, run a forward-geocode on submit so workers always get coordinates
            // they can navigate to. This is best-effort: if it fails the booking
            // still goes through, we just won't have lat/lng.
            val (resolvedLat, resolvedLng) = if (latitude != null && longitude != null) {
                latitude to longitude
            } else {
                val geo = geocoderRepository.resolveAddress(address)
                (geo?.latitude) to (geo?.longitude)
            }

            val customerNote = buildString {
                append("SĐT: $phoneNumber")
                append("\nTên: $fullName")
                if (notes.isNotBlank()) append("\nGhi chú: $notes")
            }

            val now = System.currentTimeMillis()
            val booking = Booking(
                id = UUID.randomUUID().toString(),
                customerId = currentUser.id,
                workerId = "",  // empty → toDto() sẽ convert thành null
                category = category,
                description = description,
                address = address,
                latitude = resolvedLat,
                longitude = resolvedLng,
                scheduledAt = scheduledAtMillis,
                estimatedDurationHours = 1.0,
                status = BookingStatus.BIDDING,
                type = BookingType.BIDDING,
                agreedPrice = null,
                customerNote = customerNote,
                workerNote = null,
                createdAt = now,
                updatedAt = now
            )

            when (val result = bookingRepository.createBiddingBooking(booking)) {
                is Resource.Success -> {
                    val createdBooking = result.data
                    val selectedUris = _descriptionImageUris.value

                    // Upload ảnh mô tả (nếu có)
                    if (selectedUris.isNotEmpty()) {
                        val uploadedUrls = mutableListOf<String>()
                        selectedUris.forEachIndexed { index, uri ->
                            val bytes = imageResolver(uri)
                            if (bytes != null) {
                                val fileName = "desc_${System.currentTimeMillis()}_$index.jpg"
                                val uploadResult = bookingRepository.uploadDescriptionImage(
                                    bookingId = createdBooking.id,
                                    imageBytes = bytes,
                                    fileName = fileName
                                )
                                if (uploadResult is Resource.Success) {
                                    uploadedUrls.add(uploadResult.data)
                                }
                            }
                        }
                        if (uploadedUrls.isNotEmpty()) {
                            bookingRepository.updateDescriptionImages(
                                bookingId = createdBooking.id,
                                imageUrls = uploadedUrls
                            )
                        }
                    }

                    _uiState.value = BookingUiState.Success(createdBooking.id)
                }
                is Resource.Error -> {
                    _uiState.value = BookingUiState.Error(result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun resetState() {
        _uiState.value = BookingUiState.Idle
    }
}
