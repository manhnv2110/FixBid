package com.example.fixbid.presentation.worker.jobdetail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import com.example.fixbid.domain.usecase.worker.GetJobDetailUseCase
import com.example.fixbid.domain.usecase.worker.JobDetailData
import com.example.fixbid.domain.usecase.worker.PlaceBidUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BidFormState(
    val price: String = "",
    val durationHours: String = "",
    val message: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

data class CompletionFormState(
    val note: String = "",
    val selectedImageUris: List<Uri> = emptyList(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

data class JobDetailUiState(
    val isLoading: Boolean = true,
    val data: JobDetailData? = null,
    val payment: com.example.fixbid.domain.model.Payment? = null,
    val errorMessage: String? = null,
    val showBidDialog: Boolean = false,
    val bidForm: BidFormState = BidFormState(),
    val showCompletionDialog: Boolean = false,
    val completionForm: CompletionFormState = CompletionFormState()
)

sealed interface JobDetailEvent {
    data class Toast(val message: String) : JobDetailEvent
    data object BidPlaced : JobDetailEvent
    data object CompletionSubmitted : JobDetailEvent
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getJobDetailUseCase: GetJobDetailUseCase,
    private val placeBidUseCase: PlaceBidUseCase,
    private val bookingRepository: BookingRepository,
    private val paymentRepository: com.example.fixbid.domain.repository.PaymentRepository,
    private val sendNotification: SendNotificationUseCase
) : ViewModel() {

    private val bookingId: String = savedStateHandle.get<String>("bookingId") ?: ""

    private val _uiState = MutableStateFlow(JobDetailUiState())
    val uiState: StateFlow<JobDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<JobDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = getJobDetailUseCase(bookingId)) {
                is Resource.Success -> {
                    // Pull the payment row alongside so the COMPLETED branch
                    // can render a payout receipt without a separate spinner.
                    val payment = (paymentRepository.getPaymentByBooking(bookingId)
                        as? Resource.Success)?.data
                    _uiState.update {
                        it.copy(isLoading = false, data = result.data, payment = payment)
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    fun openBidDialog() {
        val booking = _uiState.value.data?.booking ?: return
        val suggestedPrice = booking.agreedPrice?.let { it.toLong().toString() } ?: ""
        val suggestedDuration = booking.estimatedDurationHours.takeIf { it > 0 }
            ?.toString() ?: ""
        _uiState.update {
            it.copy(
                showBidDialog = true,
                bidForm = BidFormState(
                    price = suggestedPrice,
                    durationHours = suggestedDuration,
                    message = ""
                )
            )
        }
    }

    fun closeBidDialog() = _uiState.update {
        it.copy(showBidDialog = false, bidForm = BidFormState())
    }

    fun onPriceChange(value: String) = _uiState.update {
        val sanitized = value.filter { c -> c.isDigit() }
        it.copy(bidForm = it.bidForm.copy(price = sanitized, errorMessage = null))
    }

    fun onDurationChange(value: String) = _uiState.update {
        val sanitized = value.filter { c -> c.isDigit() || c == '.' || c == ',' }
            .replace(',', '.')
        it.copy(bidForm = it.bidForm.copy(durationHours = sanitized, errorMessage = null))
    }

    fun onMessageChange(value: String) = _uiState.update {
        it.copy(bidForm = it.bidForm.copy(message = value, errorMessage = null))
    }

    fun submitBid() {
        val form = _uiState.value.bidForm
        if (form.isSubmitting) return

        val price = form.price.toDoubleOrNull()
        val duration = form.durationHours.toDoubleOrNull()

        when {
            price == null || price <= 0 -> setBidError("Vui lòng nhập giá hợp lệ")
            duration == null || duration <= 0 -> setBidError("Vui lòng nhập thời gian dự kiến")
            form.message.trim().length < 10 -> setBidError("Lời giới thiệu cần ít nhất 10 ký tự")
            else -> {
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(bidForm = it.bidForm.copy(isSubmitting = true, errorMessage = null))
                    }
                    when (val result = placeBidUseCase(
                        bookingId = bookingId,
                        proposedPrice = price,
                        estimatedDurationHours = duration,
                        message = form.message
                    )) {
                        is Resource.Success -> {
                            _uiState.update {
                                it.copy(
                                    showBidDialog = false,
                                    bidForm = BidFormState()
                                )
                            }
                            _events.trySend(JobDetailEvent.Toast("Đã gửi báo giá"))
                            _events.trySend(JobDetailEvent.BidPlaced)
                            load()
                        }
                        is Resource.Error -> {
                            _uiState.update {
                                it.copy(
                                    bidForm = it.bidForm.copy(
                                        isSubmitting = false,
                                        errorMessage = result.message
                                    )
                                )
                            }
                        }
                        is Resource.Loading -> { /* no-op */ }
                    }
                }
            }
        }
    }

    private fun setBidError(msg: String) = _uiState.update {
        it.copy(bidForm = it.bidForm.copy(errorMessage = msg))
    }

    // ─── Completion flow ──────────────────────────────────────────────────────

    fun openCompletionDialog() {
        _uiState.update {
            it.copy(
                showCompletionDialog = true,
                completionForm = CompletionFormState()
            )
        }
    }

    fun closeCompletionDialog() {
        _uiState.update {
            it.copy(showCompletionDialog = false, completionForm = CompletionFormState())
        }
    }

    fun onCompletionNoteChange(value: String) = _uiState.update {
        it.copy(completionForm = it.completionForm.copy(note = value, errorMessage = null))
    }

    fun onCompletionImagesSelected(uris: List<Uri>) = _uiState.update {
        it.copy(completionForm = it.completionForm.copy(
            selectedImageUris = it.completionForm.selectedImageUris + uris,
            errorMessage = null
        ))
    }

    fun removeCompletionImage(uri: Uri) = _uiState.update {
        it.copy(completionForm = it.completionForm.copy(
            selectedImageUris = it.completionForm.selectedImageUris - uri
        ))
    }

    /**
     * Replace a previously selected completion image with a new Uri (e.g.
     * one returned from the in-app photo editor). Position in the list is
     * preserved so the worker's chosen order stays stable.
     */
    fun replaceCompletionImage(old: Uri, new: Uri) = _uiState.update {
        if (old == new) return@update it
        it.copy(completionForm = it.completionForm.copy(
            selectedImageUris = it.completionForm.selectedImageUris.map { uri ->
                if (uri == old) new else uri
            }
        ))
    }

    /**
     * Called from the screen with image bytes already resolved from URIs.
     */
    fun submitCompletion(imageBytesList: List<Pair<String, ByteArray>>) {
        val form = _uiState.value.completionForm
        if (form.isSubmitting) return

        if (form.selectedImageUris.isEmpty()) {
            _uiState.update {
                it.copy(completionForm = it.completionForm.copy(
                    errorMessage = "Vui lòng chụp ít nhất 1 ảnh thực tế"
                ))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(completionForm = it.completionForm.copy(isSubmitting = true, errorMessage = null))
            }

            // 1. Upload all images
            val uploadedUrls = mutableListOf<String>()
            for ((fileName, bytes) in imageBytesList) {
                when (val uploadResult = bookingRepository.uploadCompletionImage(bookingId, bytes, fileName)) {
                    is Resource.Success -> uploadedUrls.add(uploadResult.data)
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(completionForm = it.completionForm.copy(
                                isSubmitting = false,
                                errorMessage = "Upload ảnh thất bại: ${uploadResult.message}"
                            ))
                        }
                        return@launch
                    }
                    is Resource.Loading -> {}
                }
            }

            // 2. Submit completion with image URLs
            val note = form.note.trim().ifBlank { null }
            when (val result = bookingRepository.submitJobCompletion(bookingId, note, uploadedUrls)) {
                is Resource.Success -> {
                    // Notify the customer that the job is done and awaiting confirmation.
                    val booking = result.data
                    booking.customerId.takeIf { it.isNotBlank() }?.let { customerId ->
                        sendNotification(
                            NotificationContentFactory.jobCompletedForCustomer(
                                customerId = customerId,
                                bookingId = booking.id,
                                categoryName = booking.category.displayName
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(
                            showCompletionDialog = false,
                            completionForm = CompletionFormState()
                        )
                    }
                    _events.trySend(JobDetailEvent.Toast("Đã gửi báo cáo hoàn thành cho khách"))
                    _events.trySend(JobDetailEvent.CompletionSubmitted)
                    load()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(completionForm = it.completionForm.copy(
                            isSubmitting = false,
                            errorMessage = result.message
                        ))
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }
}