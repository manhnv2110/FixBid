package com.example.fixbid.presentation.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.VideoCall
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.CallRepository
import com.example.fixbid.data.repository.ProfileRepository
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
 * App-level controller that listens for incoming video calls and surfaces
 * an in-app dialog to the callee. Lives at the FixBidNavHost scope so the
 * dialog can pop up from anywhere in the app — booking detail, chat list,
 * worker dashboard, etc.
 *
 * Why not part of [CallViewModel]: that one is bound to the call screen
 * and only exists *after* navigation. We need to know about the call
 * BEFORE the user has a screen to show it on, hence a separate controller
 * scoped above the navhost.
 */
data class IncomingCallUiState(
    val incomingCall: VideoCall? = null,
    val callerName: String = ""
)

sealed interface IncomingCallEvent {
    /** Callee accepted — host should navigate to the call screen with this id. */
    data class Accepted(val callId: String) : IncomingCallEvent
}

@HiltViewModel
class IncomingCallController @Inject constructor(
    private val callRepository: CallRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomingCallUiState())
    val uiState: StateFlow<IncomingCallUiState> = _uiState.asStateFlow()

    private val _events = Channel<IncomingCallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var observeJob: Job? = null
    private var currentUserId: String = ""

    /**
     * Starts the listener. Idempotent — calling twice doesn't double-subscribe.
     * Driven by an explicit start() rather than init { } because the user
     * might not be authenticated yet when the controller is first created
     * (Hilt creates it lazily but eagerly observed).
     */
    fun start() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: return@launch
            currentUserId = user.id

            callRepository.observeIncomingCalls(user.id)
                .catch { /* realtime drop is non-fatal — next call will trigger a fresh subscription on app resume */ }
                .collect { call ->
                    // Only the callee should see the incoming-call dialog;
                    // never auto-pop it for the caller themselves.
                    if (call.calleeId != currentUserId) return@collect
                    val callerName = profileRepository.getProfile(call.callerId)
                        .getOrNull()
                        ?.fullName
                        ?: "Người gọi"
                    _uiState.update { it.copy(incomingCall = call, callerName = callerName) }
                }
        }
    }

    fun acceptIncoming() {
        val call = _uiState.value.incomingCall ?: return
        viewModelScope.launch {
            when (val res = callRepository.acceptCall(call.id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(incomingCall = null) }
                    _events.trySend(IncomingCallEvent.Accepted(call.id))
                }
                is Resource.Error -> {
                    // Show the toast once and clear the dialog so the user
                    // isn't stuck staring at it forever.
                    _uiState.update { it.copy(incomingCall = null) }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun rejectIncoming() {
        val call = _uiState.value.incomingCall ?: return
        viewModelScope.launch {
            callRepository.rejectCall(call.id)
            _uiState.update { it.copy(incomingCall = null) }
        }
    }

    /**
     * Manually clear without persisting a status — used when the call's
     * status changes underneath us (e.g. the caller cancelled before we
     * could decide). The per-call observer inside [CallViewModel] handles
     * the row's transition; we just need to drop the dialog.
     */
    fun dismiss() {
        _uiState.update { it.copy(incomingCall = null) }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
    }
}
