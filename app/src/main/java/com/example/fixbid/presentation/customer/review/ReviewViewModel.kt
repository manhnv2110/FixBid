package com.example.fixbid.presentation.customer.review

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.customer.GetReviewByBookingUseCase
import com.example.fixbid.domain.usecase.customer.SubmitReviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val isLoading: Boolean = true,
    val booking: Booking? = null,
    val existingReview: Review? = null,    // not null → already reviewed (read-only)
    val rating: Int = 5,
    val comment: String = "",
    val selectedImageUris: List<Uri> = emptyList(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val alreadyReviewed: Boolean get() = existingReview != null
    val canSubmit: Boolean get() = rating in 1..5 && !isSubmitting
}

sealed interface ReviewEvent {
    data class Toast(val message: String) : ReviewEvent
    data object Submitted : ReviewEvent
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingRepository: BookingRepository,
    private val getReviewByBookingUseCase: GetReviewByBookingUseCase,
    private val submitReviewUseCase: SubmitReviewUseCase
) : ViewModel() {

    private val bookingId: String = savedStateHandle.get<String>("bookingId") ?: ""

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<ReviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val bookingResult = bookingRepository.getBookingById(bookingId)
            if (bookingResult !is Resource.Success) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = (bookingResult as? Resource.Error)?.message ?: "Không tải được công việc"
                    )
                }
                return@launch
            }

            val existing = (getReviewByBookingUseCase(bookingId) as? Resource.Success)?.data

            _uiState.update {
                it.copy(
                    isLoading = false,
                    booking = bookingResult.data,
                    existingReview = existing,
                    rating = existing?.rating ?: 5,
                    comment = existing?.comment ?: ""
                )
            }
        }
    }

    fun onRatingChange(value: Int) = _uiState.update { it.copy(rating = value, errorMessage = null) }

    fun onCommentChange(value: String) = _uiState.update { it.copy(comment = value) }

    fun onImagesSelected(uris: List<Uri>) = _uiState.update {
        it.copy(selectedImageUris = (it.selectedImageUris + uris).distinct().take(5))
    }

    fun removeImage(uri: Uri) = _uiState.update {
        it.copy(selectedImageUris = it.selectedImageUris - uri)
    }

    /**
     * Submit the review. [imageBytesList] are resolved from the selected URIs by
     * the screen (filename → bytes), uploaded first, then the review is created.
     */
    fun submit(imageBytesList: List<Pair<String, ByteArray>>) {
        val state = _uiState.value
        val booking = state.booking ?: return
        if (state.isSubmitting) return
        if (state.alreadyReviewed) return

        val workerId = booking.workerId.takeIf { it.isNotBlank() }
            ?: booking.worker?.id
        if (workerId.isNullOrBlank()) {
            viewModelScope.launch { _events.send(ReviewEvent.Toast("Không tìm thấy thợ để đánh giá")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            // 1. Upload images (best-effort: a failed upload aborts with a message).
            val uploadedUrls = mutableListOf<String>()
            for ((fileName, bytes) in imageBytesList) {
                when (val up = submitReviewUseCase.uploadImage(bookingId, bytes, fileName)) {
                    is Resource.Success -> uploadedUrls.add(up.data)
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(isSubmitting = false, errorMessage = "Tải ảnh thất bại: ${up.message}")
                        }
                        return@launch
                    }
                    is Resource.Loading -> {}
                }
            }

            // 2. Create the review.
            when (val result = submitReviewUseCase(
                bookingId = bookingId,
                workerId = workerId,
                rating = state.rating,
                comment = state.comment,
                imageUrls = uploadedUrls
            )) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, existingReview = result.data) }
                    _events.send(ReviewEvent.Toast("Cảm ơn bạn đã đánh giá!"))
                    _events.send(ReviewEvent.Submitted)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }
}
