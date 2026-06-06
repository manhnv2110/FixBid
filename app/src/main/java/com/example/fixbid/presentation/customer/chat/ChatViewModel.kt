package com.example.fixbid.presentation.customer.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Message
import com.example.fixbid.domain.model.MessageType
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ChatVM"
private const val POLL_INTERVAL_MS = 3000L

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val inputText: String = "",
    val currentUserId: String = "",
    val errorMessage: String? = null,
    val counterpartAvatarUrl: String? = null
)

sealed class ChatEvent {
    data class Toast(val message: String) : ChatEvent()
    object ScrollToBottom : ChatEvent()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: com.example.fixbid.data.repository.ProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: ""
    val workerId: String = savedStateHandle["workerId"] ?: ""
    val workerName: String = run {
        val raw = savedStateHandle.get<String>("workerName") ?: "Thợ"
        runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var realtimeJob: Job? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.value = _uiState.value.copy(currentUserId = user?.id ?: "")
            markAsRead()

            if (workerId.isNotBlank()) {
                profileRepository.getProfile(workerId).onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(counterpartAvatarUrl = profile.avatarUrl)
                }
            }
        }
        // 1. Start Realtime WebSocket subscription (may take a second to connect)
        startRealtimeUpdates()
        // 2. Also start polling as immediate fallback — this guarantees the UI
        //    refreshes even if the Realtime channel hasn't connected yet or there
        //    is an RLS/publication issue on the server.
        startPolling()
    }

    private fun startRealtimeUpdates() {
        if (conversationId.isBlank()) return
        Log.d(TAG, "startRealtimeUpdates: subscribing to conversationId=$conversationId")
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collect { messages ->
                Log.d(TAG, "Realtime: received ${messages.size} messages")
                updateMessages(messages)
            }
        }
    }

    /**
     * Polling fallback — polls the DB every [POLL_INTERVAL_MS] milliseconds.
     * This guarantees the chat appears live even if Supabase Realtime has any
     * configuration issue (table not in publication, RLS blocking WS, etc.).
     *
     * When Realtime is working correctly, the two sources will emit the same
     * data and [updateMessages] will be a no-op for duplicate lists.
     */
    private fun startPolling() {
        if (conversationId.isBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }
        Log.d(TAG, "startPolling: starting ${POLL_INTERVAL_MS}ms poll for conversationId=$conversationId")
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            // Initial fetch immediately
            fetchAndUpdate()
            // Then poll repeatedly
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                fetchAndUpdate()
            }
        }
    }

    private suspend fun fetchAndUpdate() {
        when (val result = chatRepository.getMessages(conversationId)) {
            is Resource.Success -> {
                Log.d(TAG, "Poll: fetched ${result.data.size} messages")
                updateMessages(result.data)
            }
            is Resource.Error -> Log.e(TAG, "Poll fetch error: ${result.message}")
            is Resource.Loading -> {}
        }
    }

    /**
     * Merges server messages with any pending local (optimistic) messages.
     * Idempotent — if the list hasn't changed, the UI is not updated.
     */
    private fun updateMessages(serverMessages: List<Message>) {
        val pendingLocal = _uiState.value.messages.filter { local ->
            local.id.startsWith(LOCAL_PREFIX) &&
                serverMessages.none { m -> m.sameContent(local) }
        }
        val merged = (serverMessages + pendingLocal)
            .sortedBy { it.createdAt }
            .distinctBy { it.id }

        // Only update UI if content changed (avoid unnecessary recompositions)
        val current = _uiState.value.messages
        if (merged.size != current.size || merged != current) {
            _uiState.value = _uiState.value.copy(
                messages = merged,
                isLoading = false
            )
            if (merged.isNotEmpty()) {
                viewModelScope.launch { _events.emit(ChatEvent.ScrollToBottom) }
            }
        }
        markAsRead()
    }

    private fun Message.sameContent(other: Message): Boolean =
        senderId == other.senderId &&
            content == other.content &&
            kotlin.math.abs(createdAt - other.createdAt) < 60_000L

    private fun markAsRead() {
        val uid = _uiState.value.currentUserId
        if (uid.isBlank() || conversationId.isBlank()) return
        viewModelScope.launch {
            chatRepository.markAsRead(conversationId, uid)
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() || state.isSending) return
        if (conversationId.isBlank() || state.currentUserId.isBlank()) return

        val now = System.currentTimeMillis()
        val optimistic = Message(
            id             = "$LOCAL_PREFIX$now",
            conversationId = conversationId,
            senderId       = state.currentUserId,
            recipientId    = workerId,
            content        = text,
            type           = MessageType.TEXT,
            imageUrl       = null,
            isRead         = false,
            createdAt      = now
        )
        _uiState.value = state.copy(
            inputText = "",
            isSending = true,
            messages = (state.messages + optimistic).sortedBy { it.createdAt }
        )
        viewModelScope.launch { _events.emit(ChatEvent.ScrollToBottom) }

        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(optimistic.copy(id = ""))) {
                is Resource.Success -> {
                    Log.d(TAG, "sendMessage: success id=${result.data.id}")
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        messages = _uiState.value.messages
                            .map { if (it.id == optimistic.id) result.data else it }
                            .distinctBy { it.id }
                            .sortedBy { it.createdAt }
                    )
                    // Force immediate poll so the recipient sees the new message quickly
                    fetchAndUpdate()
                }
                is Resource.Error -> {
                    Log.e(TAG, "sendMessage: failed ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        messages = _uiState.value.messages.filterNot { it.id == optimistic.id }
                    )
                    _events.emit(ChatEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        pollJob?.cancel()
    }

    private companion object {
        const val LOCAL_PREFIX = "local_"
    }
}
