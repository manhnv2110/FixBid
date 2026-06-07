package com.example.fixbid.presentation.customer.booking

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import com.example.fixbid.data.location.GeocoderRepository
import com.example.fixbid.data.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerBookingDetailUiState(
    val isLoading: Boolean = true,
    val booking: Booking? = null,
    val payment: Payment? = null,
    val errorMessage: String? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val editCategory: ServiceCategory = ServiceCategory.OTHER,
    val editDescription: String = "",
    val editAddress: String = "",
    val editLatitude: Double? = null,
    val editLongitude: Double? = null,
    val editScheduledAt: Long = 0L,
    val editFullName: String = "",
    val editPhone: String = "",
    val editNotes: String = "",
    val editImageUris: List<Uri> = emptyList(),
    val isCancelling: Boolean = false,
    val isDeleting: Boolean = false,
    /** True while accept/reject quote network call is in flight. */
    val isRespondingQuote: Boolean = false
)

sealed interface CustomerBookingDetailEvent {
    data class Toast(val message: String) : CustomerBookingDetailEvent
    data object BookingDeleted : CustomerBookingDetailEvent
}

@HiltViewModel
class CustomerBookingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val geocoderRepository: GeocoderRepository,
    private val locationRepository: LocationRepository,
    private val sendNotification: SendNotificationUseCase,
    private val acceptDirectQuoteUseCase: com.example.fixbid.domain.usecase.customer.AcceptDirectQuoteUseCase,
    private val rejectDirectQuoteUseCase: com.example.fixbid.domain.usecase.customer.RejectDirectQuoteUseCase,
    private val aiAgentRepository: com.example.fixbid.domain.repository.AiAgentRepository,
    private val authRepository: com.example.fixbid.domain.repository.AuthRepository,
    private val aiSuggestionEngine: com.example.fixbid.domain.usecase.shared.AiSuggestionEngine
) : ViewModel() {

    val geocoder: GeocoderRepository get() = geocoderRepository
    val locator: LocationRepository get() = locationRepository

    private val bookingId: String = savedStateHandle.get<String>("bookingId") ?: ""

    private val _uiState = MutableStateFlow(CustomerBookingDetailUiState())
    val uiState: StateFlow<CustomerBookingDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<CustomerBookingDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Lazily-initialised AI shortcut controller. Lives for the ViewModel's
     * lifetime; emits inline analysis state and pending nav/prefill intents
     * the screen consumes.
     */
    val aiController: com.example.fixbid.presentation.ai.AiSuggestionController by lazy {
        // We can't read the role synchronously here so the controller starts
        // as CUSTOMER (this whole VM is customer-only anyway).
        com.example.fixbid.presentation.ai.AiSuggestionController(
            scope = viewModelScope,
            aiAgentRepository = aiAgentRepository,
            role = com.example.fixbid.domain.model.UserRole.CUSTOMER
        )
    }

    /**
     * Compute AI suggestions from the latest booking snapshot. Recomputes
     * inside the screen via `remember(uiState.booking?.status)` so the chip
     * row stays in sync as the realtime observer pushes status transitions.
     */
    fun aiSuggestions(): List<com.example.fixbid.domain.model.AiSuggestion> {
        val booking = _uiState.value.booking ?: return emptyList()
        return aiSuggestionEngine(
            com.example.fixbid.domain.model.AiContext(
                screen = com.example.fixbid.domain.model.AiContextScreen.CUSTOMER_BOOKING_DETAIL,
                userRole = com.example.fixbid.domain.model.UserRole.CUSTOMER,
                data = mapOf(
                    "bookingId" to booking.id,
                    "bookingStatus" to booking.status.name,
                    "bookingType" to booking.type.name,
                    "category" to booking.category.displayName,
                    "quotedPrice" to booking.quotedPrice,
                    "agreedPrice" to booking.agreedPrice,
                    "address" to booking.address,
                    "scheduledAt" to booking.scheduledAt,
                    "workerName" to booking.worker?.fullName,
                    "workerId" to booking.workerId.takeIf { it.isNotBlank() }
                )
            )
        )
    }

    init {
        loadBooking()
        observeBookingRealtime()
    }

    fun loadBooking() {
        if (bookingId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "ID công việc không hợp lệ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = bookingRepository.getBookingById(bookingId)) {
                is Resource.Success -> {
                    val booking = result.data
                    // Payment is best-effort: missing means cash booking or
                    // pre-payment state — don't block the UI on it.
                    val payment = (paymentRepository.getPaymentByBooking(bookingId)
                        as? Resource.Success)?.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            booking = booking,
                            payment = payment,
                            editCategory = booking.category,
                            editDescription = booking.description,
                            editAddress = booking.address,
                            editLatitude = booking.latitude,
                            editLongitude = booking.longitude,
                            editScheduledAt = booking.scheduledAt,
                            editFullName = extractNameFromNote(booking.customerNote),
                            editPhone = extractPhoneFromNote(booking.customerNote),
                            editNotes = extractNotesFromNote(booking.customerNote)
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun toggleEditMode() {
        val current = _uiState.value
        _uiState.update { it.copy(isEditing = !current.isEditing) }
    }

    /**
     * Live-updates the booking while the screen is open so status transitions
     * driven by the worker or the payment flow (awaiting payment → confirmed →
     * in progress → pending completion → completed) appear without a manual
     * reload. To avoid clobbering data the user is actively editing, the live
     * value is only applied when not in edit mode; the edit form fields are left
     * untouched. Payment is re-fetched on status changes so the detail card and
     * the action buttons (pay / confirm completion) stay in sync.
     */
    private fun observeBookingRealtime() {
        if (bookingId.isBlank()) return
        viewModelScope.launch {
            bookingRepository.observeBooking(bookingId)
                .catch { /* realtime drop is non-fatal: the one-time load already populated the UI */ }
                .collect { booking ->
                    if (booking == null) return@collect
                    // Don't overwrite anything while the user is editing the form.
                    if (_uiState.value.isEditing) return@collect

                    val previousStatus = _uiState.value.booking?.status
                    // Refresh payment only when the status actually changed, to
                    // avoid an extra network call on every realtime echo.
                    val payment = if (previousStatus != booking.status) {
                        (paymentRepository.getPaymentByBooking(bookingId) as? Resource.Success)?.data
                            ?: _uiState.value.payment
                    } else {
                        _uiState.value.payment
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            booking = booking,
                            payment = payment,
                            editCategory = booking.category,
                            editDescription = booking.description,
                            editAddress = booking.address,
                            editLatitude = booking.latitude,
                            editLongitude = booking.longitude,
                            editScheduledAt = booking.scheduledAt,
                            editFullName = extractNameFromNote(booking.customerNote),
                            editPhone = extractPhoneFromNote(booking.customerNote),
                            editNotes = extractNotesFromNote(booking.customerNote)
                        )
                    }
                }
        }
    }

    fun onDescriptionChange(desc: String) {
        _uiState.update { it.copy(editDescription = desc) }
    }

    fun onCategoryChange(cat: ServiceCategory) {
        _uiState.update { it.copy(editCategory = cat) }
    }

    fun onAddressChange(addr: String, lat: Double?, lng: Double?) {
        _uiState.update { it.copy(editAddress = addr, editLatitude = lat, editLongitude = lng) }
    }

    fun onScheduledAtChange(millis: Long) {
        _uiState.update { it.copy(editScheduledAt = millis) }
    }

    fun onFullNameChange(name: String) {
        _uiState.update { it.copy(editFullName = name) }
    }

    fun onPhoneChange(phone: String) {
        _uiState.update { it.copy(editPhone = phone) }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(editNotes = notes) }
    }

    fun onImagesSelected(uris: List<Uri>) {
        _uiState.update { it.copy(editImageUris = it.editImageUris + uris) }
    }

    fun removeImage(uri: Uri) {
        _uiState.update { it.copy(editImageUris = it.editImageUris - uri) }
    }

    fun saveChanges(imageResolver: (Uri) -> ByteArray?) {
        val current = _uiState.value
        val booking = current.booking ?: return
        if (current.editDescription.isBlank() || current.editAddress.isBlank() ||
            current.editFullName.isBlank() || current.editPhone.isBlank()) {
            _events.trySend(CustomerBookingDetailEvent.Toast("Vui lòng điền đầy đủ các thông tin bắt buộc"))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // Rebuild customerNote format:
            val updatedNote = buildString {
                append("SĐT: ${current.editPhone}")
                append("\nTên: ${current.editFullName}")
                if (current.editNotes.isNotBlank()) append("\nGhi chú: ${current.editNotes}")
            }

            // Upload description images if any new ones are selected
            val finalImageUrls = booking.descriptionImages?.toMutableList() ?: mutableListOf()
            if (current.editImageUris.isNotEmpty()) {
                current.editImageUris.forEachIndexed { index, uri ->
                    val bytes = imageResolver(uri)
                    if (bytes != null) {
                        val fileName = "desc_edit_${System.currentTimeMillis()}_$index.jpg"
                        val uploadResult = bookingRepository.uploadDescriptionImage(
                            bookingId = booking.id,
                            imageBytes = bytes,
                            fileName = fileName
                        )
                        if (uploadResult is Resource.Success) {
                            finalImageUrls.add(uploadResult.data)
                        }
                    }
                }
            }

            val updatedBooking = booking.copy(
                category = current.editCategory,
                description = current.editDescription,
                address = current.editAddress,
                latitude = current.editLatitude,
                longitude = current.editLongitude,
                scheduledAt = current.editScheduledAt,
                customerNote = updatedNote,
                descriptionImages = finalImageUrls,
                updatedAt = System.currentTimeMillis()
            )

            when (val result = bookingRepository.updateBooking(updatedBooking)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isEditing = false,
                            booking = result.data,
                            editImageUris = emptyList()
                        )
                    }
                    _events.trySend(CustomerBookingDetailEvent.Toast("Đã lưu thay đổi"))
                    loadBooking()
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.trySend(CustomerBookingDetailEvent.Toast("Lỗi khi lưu: ${result.message}"))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun removeExistingImage(url: String) {
        val current = _uiState.value
        val booking = current.booking ?: return
        val updatedImages = booking.descriptionImages?.filter { it != url } ?: emptyList()
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = bookingRepository.updateDescriptionImages(booking.id, updatedImages)
            if (result is Resource.Success) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        booking = result.data
                    )
                }
                _events.trySend(CustomerBookingDetailEvent.Toast("Đã xoá ảnh"))
            } else {
                _uiState.update { it.copy(isSaving = false) }
                _events.trySend(CustomerBookingDetailEvent.Toast("Xoá ảnh thất bại"))
            }
        }
    }

    fun cancelBooking(reason: String = "") {
        val booking = _uiState.value.booking ?: return
        val finalReason = reason.trim().ifBlank { "Khách hàng hủy yêu cầu" }
        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true) }
            when (val result = bookingRepository.cancelBooking(booking.id, finalReason)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isCancelling = false) }
                    // Notify the assigned worker (if any) that the booking was cancelled.
                    booking.workerId.takeIf { it.isNotBlank() }?.let { workerId ->
                        sendNotification(
                            NotificationContentFactory.bookingCancelledForUser(
                                userId = workerId,
                                bookingId = booking.id,
                                categoryName = booking.category.displayName,
                                reason = finalReason
                            )
                        )
                    }
                    _events.trySend(CustomerBookingDetailEvent.Toast("Đã hủy yêu cầu thành công"))
                    loadBooking()
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isCancelling = false) }
                    _events.trySend(CustomerBookingDetailEvent.Toast("Hủy yêu cầu thất bại: ${result.message}"))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteBooking() {
        val booking = _uiState.value.booking ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            when (val result = bookingRepository.deleteBooking(booking.id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isDeleting = false) }
                    _events.trySend(CustomerBookingDetailEvent.Toast("Đã xóa yêu cầu thành công"))
                    _events.trySend(CustomerBookingDetailEvent.BookingDeleted)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isDeleting = false) }
                    _events.trySend(CustomerBookingDetailEvent.Toast("Xóa yêu cầu thất bại: ${result.message}"))
                }
                is Resource.Loading -> {}
            }
        }
    }

    // ─── Direct booking quote response ──────────────────────────────────────
    //
    // Customer accepts/rejects the worker's quote on a direct booking. Accepting
    // copies quoted_price → agreed_price and moves the booking to AWAITING_PAYMENT
    // (so the payment screen can finally render with a non-null amount). Rejecting
    // rolls back to PENDING with the customer's reason persisted in worker_note,
    // letting the worker either re-quote or decline the job.

    fun acceptQuote() {
        val booking = _uiState.value.booking ?: return
        if (_uiState.value.isRespondingQuote) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRespondingQuote = true) }
            when (val result = acceptDirectQuoteUseCase(booking.id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isRespondingQuote = false, booking = result.data)
                    }
                    _events.trySend(
                        CustomerBookingDetailEvent.Toast(
                            "Đã chấp nhận báo giá. Vui lòng thanh toán để xác nhận lịch."
                        )
                    )
                    loadBooking()
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isRespondingQuote = false) }
                    _events.trySend(
                        CustomerBookingDetailEvent.Toast(
                            "Chấp nhận báo giá thất bại: ${result.message}"
                        )
                    )
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    fun rejectQuote(reason: String?) {
        val booking = _uiState.value.booking ?: return
        if (_uiState.value.isRespondingQuote) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRespondingQuote = true) }
            when (val result = rejectDirectQuoteUseCase(booking.id, reason)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isRespondingQuote = false, booking = result.data)
                    }
                    _events.trySend(
                        CustomerBookingDetailEvent.Toast(
                            "Đã từ chối báo giá. Thợ có thể gửi báo giá khác hoặc huỷ đơn."
                        )
                    )
                    loadBooking()
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isRespondingQuote = false) }
                    _events.trySend(
                        CustomerBookingDetailEvent.Toast(
                            "Từ chối báo giá thất bại: ${result.message}"
                        )
                    )
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    private fun extractNameFromNote(note: String?): String {
        if (note == null) return ""
        val lines = note.lines()
        val nameLine = lines.find { it.startsWith("Tên: ") }
        return nameLine?.substringAfter("Tên: ") ?: ""
    }

    private fun extractPhoneFromNote(note: String?): String {
        if (note == null) return ""
        val lines = note.lines()
        val phoneLine = lines.find { it.startsWith("SĐT: ") }
        return phoneLine?.substringAfter("SĐT: ") ?: ""
    }

    private fun extractNotesFromNote(note: String?): String {
        if (note == null) return ""
        val lines = note.lines()
        val notesLine = lines.find { it.startsWith("Ghi chú: ") }
        return notesLine?.substringAfter("Ghi chú: ") ?: ""
    }
}
