package com.example.fixbid.presentation.worker.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review
import com.example.fixbid.domain.usecase.worker.GetWorkerReviewsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkerReviewsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val reviews: List<Review> = emptyList(),
    val errorMessage: String? = null,
    val replyingTo: Review? = null,
    val replyText: String = "",
    val isSubmittingReply: Boolean = false
) {
    val averageRating: Double
        get() = if (reviews.isEmpty()) 0.0 else reviews.sumOf { it.rating }.toDouble() / reviews.size

    /** rating value (1..5) → count, used for the distribution bars. */
    val distribution: Map<Int, Int>
        get() = (1..5).associateWith { star -> reviews.count { it.rating == star } }
}

sealed interface WorkerReviewsEvent {
    data class Toast(val message: String) : WorkerReviewsEvent
}

@HiltViewModel
class WorkerReviewsViewModel @Inject constructor(
    private val getWorkerReviewsUseCase: GetWorkerReviewsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerReviewsUiState())
    val uiState: StateFlow<WorkerReviewsUiState> = _uiState.asStateFlow()

    private val _events = Channel<WorkerReviewsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !refresh, isRefreshing = refresh, errorMessage = null) }
            when (val result = getWorkerReviewsUseCase()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, reviews = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun refresh() = load(refresh = true)

    fun openReply(review: Review) = _uiState.update {
        it.copy(replyingTo = review, replyText = review.workerReply ?: "")
    }

    fun closeReply() = _uiState.update { it.copy(replyingTo = null, replyText = "") }

    fun onReplyTextChange(value: String) = _uiState.update { it.copy(replyText = value) }

    fun submitReply() {
        val target = _uiState.value.replyingTo ?: return
        val text = _uiState.value.replyText
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReply = true) }
            when (val result = getWorkerReviewsUseCase.reply(target.id, text)) {
                is Resource.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isSubmittingReply = false,
                            replyingTo = null,
                            replyText = "",
                            reviews = state.reviews.map { if (it.id == result.data.id) result.data else it }
                        )
                    }
                    _events.send(WorkerReviewsEvent.Toast("Đã gửi phản hồi"))
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSubmittingReply = false) }
                    _events.send(WorkerReviewsEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }
}
