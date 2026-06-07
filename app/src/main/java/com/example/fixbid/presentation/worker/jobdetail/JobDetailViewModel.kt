package com.example.fixbid.presentation.worker.jobdetail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import com.example.fixbid.domain.usecase.worker.AcceptDirectBookingUseCase
import com.example.fixbid.domain.usecase.worker.DeclineDirectBookingUseCase
import com.example.fixbid.domain.usecase.worker.GetJobDetailUseCase
import com.example.fixbid.domain.usecase.worker.JobDetailData
import com.example.fixbid.domain.usecase.worker.PlaceBidUseCase
import com.example.fixbid.domain.usecase.worker.QuoteDirectBookingUseCase
import com.example.fixbid.domain.usecase.worker.WorkerCancelBookingUseCase
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

data class CancelFormState(
    val reason: String = "",
    val errorMessage: String? = null
) {
    val trimmedReason: String get() = reason.trim()
    val isValid: Boolean get() = trimmedReason.length >= 10
}

/**
 * Form state for the worker's price quote on a direct booking. Mirrors the
 * bid form (price, duration, message) but submits to the dedicated direct-quote
 * endpoint so the booking advances to QUOTED instead of creating a Bid row.
 */
data class QuoteFormState(
    val price: String = "",
    val durationHours: String = "",
    val message: String = "",
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
    val completionForm: CompletionFormState = CompletionFormState(),
    /** True while an accept/decline call for a DIRECT booking is in flight. */
    val isRespondingDirect: Boolean = false,
    val showDeclineDialog: Boolean = false,
    val showCancelDialog: Boolean = false,
    val cancelForm: CancelFormState = CancelFormState(),
    val isCancelling: Boolean = false,
    /** True while the worker's price-quote submit is in flight. */
    val showQuoteDialog: Boolean = false,
    val quoteForm: QuoteFormState = QuoteFormState(),
    /**
     * Current authenticated user id, used by the screen to gate the cancel
     * button visibility and the cancelled banner predicate
     * (`booking.workerId == currentUserId`). Null while the session is still
     * resolving — visibility predicates default to false in that window.
     */
    val currentUserId: String? = null
)

sealed interface JobDetailEvent {
    data class Toast(val message: String) : JobDetailEvent
    data object BidPlaced : JobDetailEvent
    data object CompletionSubmitted : JobDetailEvent
    data object DirectBookingAccepted : JobDetailEvent
    data object DirectBookingDeclined : JobDetailEvent
    data object DirectQuoteSent : JobDetailEvent
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getJobDetailUseCase: GetJobDetailUseCase,
    private val placeBidUseCase: PlaceBidUseCase,
    private val acceptDirectBookingUseCase: AcceptDirectBookingUseCase,
    private val declineDirectBookingUseCase: DeclineDirectBookingUseCase,
    private val quoteDirectBookingUseCase: QuoteDirectBookingUseCase,
    private val bookingRepository: BookingRepository,
    private val paymentRepository: com.example.fixbid.domain.repository.PaymentRepository,
    private val sendNotification: SendNotificationUseCase,
    private val workerCancelBookingUseCase: WorkerCancelBookingUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val bookingId: String = savedStateHandle.get<String>("bookingId") ?: ""

    private val _uiState = MutableStateFlow(JobDetailUiState())
    val uiState: StateFlow<JobDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<JobDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadCurrentUser()
        load()
        observeBookingRealtime()
    }

    /**
     * Resolve the authenticated user id once on init so the screen can render
     * the cancel button + cancelled banner without each composable poking the
     * auth repository directly. Failure is non-fatal — the predicates simply
     * stay false until a future reload populates the id.
     */
    private fun loadCurrentUser() {
        viewModelScope.launch {
            val id = authRepository.getCurrentUser()?.id
            if (id != null) {
                _uiState.update { it.copy(currentUserId = id) }
            }
        }
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

    /**
     * Refresh the job detail in place, without flipping the full-screen loading
     * spinner. Used by the realtime observer so a status change pushed from the
     * customer side (bid accepted → awaiting payment → confirmed, or completion
     * confirmed) re-renders the action buttons silently. Open dialogs and the
     * bid/completion forms are preserved.
     */
    private fun refresh() {
        viewModelScope.launch {
            when (val result = getJobDetailUseCase(bookingId)) {
                is Resource.Success -> {
                    val payment = (paymentRepository.getPaymentByBooking(bookingId)
                        as? Resource.Success)?.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            data = result.data,
                            payment = payment
                        )
                    }
                }
                // A transient refresh failure is non-fatal: keep showing the last
                // good data rather than blanking the screen with an error.
                is Resource.Error -> Unit
                is Resource.Loading -> Unit
            }
        }
    }

    /**
     * Live-updates the job while the worker has it open. Supabase realtime fires
     * whenever this booking row changes, so the worker sees the customer's
     * actions (their bid getting accepted, payment landing, completion being
     * confirmed/rejected) reflected in the status banner and action buttons
     * without leaving the screen.
     */
    private fun observeBookingRealtime() {
        if (bookingId.isBlank()) return
        viewModelScope.launch {
            var lastStatus: BookingStatus? = null
            bookingRepository.observeBooking(bookingId)
                .catch { /* realtime drop is non-fatal: the one-time load populated the UI */ }
                .collect { booking ->
                    if (booking == null) return@collect
                    // Recompute the full detail (bids/stats/myBid + payment) only
                    // when the status actually advanced, to avoid redundant fetches
                    // on every echo of the same row.
                    if (booking.status != lastStatus) {
                        lastStatus = booking.status
                        refresh()
                    }
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

    // ─── Direct booking accept / decline ─────────────────────────────────────

    /**
     * Worker accepts a DIRECT booking the customer assigned to them. Backend
     * flips status to AWAITING_PAYMENT (customer pays next) and the customer
     * gets a push notification. Realtime then updates this screen automatically.
     */
    fun acceptDirectBooking() {
        if (_uiState.value.isRespondingDirect) return
        val bookingIdLocal = bookingId
        viewModelScope.launch {
            _uiState.update { it.copy(isRespondingDirect = true) }
            when (val result = acceptDirectBookingUseCase(bookingIdLocal)) {
                is Resource.Success -> {
                    _events.trySend(JobDetailEvent.Toast("Đã nhận đơn — chờ khách thanh toán"))
                    _events.trySend(JobDetailEvent.DirectBookingAccepted)
                    load()
                }
                is Resource.Error -> {
                    _events.trySend(JobDetailEvent.Toast(result.message))
                }
                is Resource.Loading -> { /* no-op */ }
            }
            _uiState.update { it.copy(isRespondingDirect = false) }
        }
    }

    fun openDeclineDialog() = _uiState.update { it.copy(showDeclineDialog = true) }
    fun closeDeclineDialog() = _uiState.update { it.copy(showDeclineDialog = false) }

    /**
     * Worker declines with a reason. Reason is stored in `cancel_reason`, the
     * customer is notified, and the screen is reloaded so the action buttons
     * collapse to the read-only "đã huỷ" banner.
     */
    fun declineDirectBooking(reason: String) {
        if (_uiState.value.isRespondingDirect) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRespondingDirect = true, showDeclineDialog = false) }
            when (val result = declineDirectBookingUseCase(bookingId, reason)) {
                is Resource.Success -> {
                    _events.trySend(JobDetailEvent.Toast("Đã từ chối đơn"))
                    _events.trySend(JobDetailEvent.DirectBookingDeclined)
                    load()
                }
                is Resource.Error -> {
                    _events.trySend(JobDetailEvent.Toast(result.message))
                }
                is Resource.Loading -> { /* no-op */ }
            }
            _uiState.update { it.copy(isRespondingDirect = false) }
        }
    }

    // ─── Direct booking quote flow ───────────────────────────────────────────
    //
    // Worker proposes a price + duration on a direct booking. Submitting moves
    // the booking to QUOTED so the customer can review and accept/reject. If the
    // customer rejects (booking comes back to PENDING with worker_note holding
    // the rejection reason), the worker can re-open this dialog and submit a
    // revised quote.

    /**
     * Open the quote dialog with sensible defaults: the worker's per-hour rate
     * × the booking's estimated duration as a starting price, the customer's
     * estimated duration, and an empty message. If the booking is being
     * re-quoted (status = QUOTED), pre-fill the previous quote so small edits
     * don't require re-typing everything.
     */
    fun openQuoteDialog() {
        val booking = _uiState.value.data?.booking ?: return
        val previousPrice = booking.quotedPrice
        val previousDuration = booking.quoteEstimatedDurationHours
        val previousMessage = booking.quoteMessage.orEmpty()
        _uiState.update {
            it.copy(
                showQuoteDialog = true,
                quoteForm = QuoteFormState(
                    price = previousPrice?.toLong()?.toString().orEmpty(),
                    durationHours = (previousDuration ?: booking.estimatedDurationHours)
                        .takeIf { d -> d > 0 }?.toString().orEmpty(),
                    message = previousMessage
                )
            )
        }
    }

    fun closeQuoteDialog() = _uiState.update {
        it.copy(showQuoteDialog = false, quoteForm = QuoteFormState())
    }

    fun onQuotePriceChange(value: String) = _uiState.update {
        val sanitized = value.filter(Char::isDigit)
        it.copy(quoteForm = it.quoteForm.copy(price = sanitized, errorMessage = null))
    }

    fun onQuoteDurationChange(value: String) = _uiState.update {
        val sanitized = value.filter { c -> c.isDigit() || c == '.' || c == ',' }
            .replace(',', '.')
        it.copy(quoteForm = it.quoteForm.copy(durationHours = sanitized, errorMessage = null))
    }

    fun onQuoteMessageChange(value: String) = _uiState.update {
        it.copy(quoteForm = it.quoteForm.copy(message = value, errorMessage = null))
    }

    fun submitQuote() {
        val form = _uiState.value.quoteForm
        if (form.isSubmitting) return

        val price = form.price.toDoubleOrNull()
        val duration = form.durationHours.toDoubleOrNull()
        when {
            price == null || price <= 0 -> {
                _uiState.update {
                    it.copy(quoteForm = it.quoteForm.copy(errorMessage = "Vui lòng nhập giá hợp lệ"))
                }
            }
            duration != null && duration <= 0 -> {
                _uiState.update {
                    it.copy(quoteForm = it.quoteForm.copy(errorMessage = "Thời gian dự kiến không hợp lệ"))
                }
            }
            form.message.trim().length < 10 -> {
                _uiState.update {
                    it.copy(quoteForm = it.quoteForm.copy(errorMessage = "Lời nhắn cần ít nhất 10 ký tự"))
                }
            }
            else -> {
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(quoteForm = it.quoteForm.copy(isSubmitting = true, errorMessage = null))
                    }
                    when (val result = quoteDirectBookingUseCase(
                        bookingId = bookingId,
                        proposedPrice = price,
                        message = form.message,
                        estimatedDurationHours = duration
                    )) {
                        is Resource.Success -> {
                            _uiState.update {
                                it.copy(showQuoteDialog = false, quoteForm = QuoteFormState())
                            }
                            _events.trySend(JobDetailEvent.Toast("Đã gửi báo giá cho khách"))
                            _events.trySend(JobDetailEvent.DirectQuoteSent)
                            load()
                        }
                        is Resource.Error -> {
                            _uiState.update {
                                it.copy(quoteForm = it.quoteForm.copy(
                                    isSubmitting = false,
                                    errorMessage = result.message
                                ))
                            }
                        }
                        is Resource.Loading -> { /* no-op */ }
                    }
                }
            }
        }
    }

    // ─── Worker cancel after payment ─────────────────────────────────────────

    /**
     * Open the cancel dialog with a fresh form. Used from the "Hủy đơn" button
     * that's only visible when `booking.status == CONFIRMED` and the booking
     * belongs to the current worker.
     */
    fun openCancelDialog() = _uiState.update {
        it.copy(showCancelDialog = true, cancelForm = CancelFormState())
    }

    /** Dismiss the cancel dialog without invoking the use case. */
    fun closeCancelDialog() = _uiState.update {
        it.copy(showCancelDialog = false, cancelForm = CancelFormState())
    }

    fun onCancelReasonChange(value: String) = _uiState.update {
        it.copy(cancelForm = it.cancelForm.copy(reason = value, errorMessage = null))
    }

    /**
     * Invoke [WorkerCancelBookingUseCase] with the current reason. On success,
     * close the dialog and reload the detail so the cancelled banner replaces
     * the action buttons. On error, surface the message inside the dialog and
     * re-enable the buttons so the worker can retry or amend the reason.
     */
    fun confirmCancel() {
        if (_uiState.value.isCancelling) return
        val form = _uiState.value.cancelForm
        if (!form.isValid) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCancelling = true,
                    cancelForm = it.cancelForm.copy(errorMessage = null)
                )
            }
            when (val result = workerCancelBookingUseCase(bookingId, form.reason)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isCancelling = false,
                            showCancelDialog = false,
                            cancelForm = CancelFormState()
                        )
                    }
                    _events.trySend(JobDetailEvent.Toast("Đã hủy đơn — tiền đã hoàn cho khách"))
                    load()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isCancelling = false,
                            cancelForm = it.cancelForm.copy(errorMessage = result.message)
                        )
                    }
                }
                is Resource.Loading -> {
                    _uiState.update { it.copy(isCancelling = false) }
                }
            }
        }
    }
}