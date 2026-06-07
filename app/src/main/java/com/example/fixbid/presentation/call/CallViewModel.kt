package com.example.fixbid.presentation.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.CallStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.VideoCall
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the in-call screen. Two roles share one VM/screen so the lifecycle
 * is simpler:
 *   - **Caller**: lands here right after `startCall()`, status = RINGING,
 *     waits for the row to flip to ACCEPTED → renders the WebView.
 *   - **Callee**: lands here after tapping Accept on the global incoming-
 *     call dialog; status is already ACCEPTED → WebView mounts immediately.
 *
 * Both observe the same row via [CallRepository.observeCall] so any
 * status change (the other party hangs up, the row times out, etc.)
 * flows back into [uiState] and the screen reacts.
 */
data class CallUiState(
    val isLoading: Boolean = true,
    val call: VideoCall? = null,
    val currentUserId: String = "",
    val errorMessage: String? = null,
    /** True the moment the WebView should be mounted (status == ACCEPTED). */
    val joinRoom: Boolean = false,
    /** True after either side ends the call — screen shows summary then closes. */
    val isFinished: Boolean = false,
    /** Seconds since the call was answered — shown in the in-call header. */
    val elapsedSeconds: Int = 0
)

sealed interface CallEvent {
    data class Toast(val message: String) : CallEvent
    /** Navigate the user away from the call screen. */
    data object Close : CallEvent
}

@HiltViewModel
class CallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val callRepository: CallRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val callId: String = savedStateHandle.get<String>("callId") ?: ""

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val _events = Channel<CallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var observeJob: Job? = null
    private var tickJob: Job? = null

    init {
        if (callId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Cuộc gọi không hợp lệ") }
        } else {
            bootstrap()
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.update { it.copy(currentUserId = user?.id.orEmpty()) }
            // One-shot fetch first so we have an initial state even if
            // realtime is slow to connect.
            when (val res = callRepository.getCall(callId)) {
                is Resource.Success -> applyCall(res.data)
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.message)
                }
                is Resource.Loading -> Unit
            }
            observeRealtime()
        }
    }

    private fun observeRealtime() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            callRepository.observeCall(callId)
                .catch { /* realtime drop is non-fatal — we still have the initial fetch */ }
                .collect { call ->
                    if (call != null) applyCall(call)
                }
        }
    }

    private fun applyCall(call: VideoCall) {
        val current = _uiState.value
        _uiState.update {
            it.copy(
                isLoading = false,
                call = call,
                joinRoom = call.status == CallStatus.ACCEPTED,
                isFinished = call.status in setOf(
                    CallStatus.ENDED, CallStatus.REJECTED, CallStatus.MISSED
                )
            )
        }

        // Start the elapsed-seconds ticker exactly once when we transition
        // into ACCEPTED. The Jitsi WebView keeps its own clock — this one
        // is only for the small in-call header overlay.
        if (call.status == CallStatus.ACCEPTED && call.answeredAt != null && tickJob == null) {
            tickJob = viewModelScope.launch {
                val start = call.answeredAt
                while (true) {
                    val elapsed = ((System.currentTimeMillis() - start) / 1000).toInt()
                    _uiState.update { it.copy(elapsedSeconds = elapsed.coerceAtLeast(0)) }
                    kotlinx.coroutines.delay(1000)
                }
            }
        }

        // Auto-close when the row reaches a terminal status. We delay the
        // close event slightly so the user sees a "Cuộc gọi đã kết thúc"
        // toast / summary rather than a hard pop.
        if (call.status in setOf(CallStatus.ENDED, CallStatus.REJECTED, CallStatus.MISSED) &&
            current.call?.status != call.status
        ) {
            tickJob?.cancel()
            tickJob = null
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                _events.trySend(CallEvent.Close)
            }
        }
    }

    /** Caller cancel BEFORE callee picks up. */
    fun cancelOutgoing() {
        viewModelScope.launch {
            when (val res = callRepository.rejectCall(callId)) {
                is Resource.Error -> _events.trySend(CallEvent.Toast(res.message))
                else -> Unit
            }
            _events.trySend(CallEvent.Close)
        }
    }

    /** End an in-progress call — sends duration to backend so chat log shows "02:34". */
    fun hangUp() {
        val call = _uiState.value.call ?: run {
            _events.trySend(CallEvent.Close)
            return
        }
        val duration = if (call.answeredAt != null) {
            ((System.currentTimeMillis() - call.answeredAt) / 1000).toInt().coerceAtLeast(0)
        } else 0
        viewModelScope.launch {
            callRepository.endCall(callId, duration)
            _events.trySend(CallEvent.Close)
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
        tickJob?.cancel()
    }
}
