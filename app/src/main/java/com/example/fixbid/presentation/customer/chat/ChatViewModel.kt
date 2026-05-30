package com.example.fixbid.presentation.customer.chat

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val inputText: String = "",
    val currentUserId: String = "",
    val errorMessage: String? = null
)

sealed class ChatEvent {
    data class Toast(val message: String) : ChatEvent()
    object ScrollToBottom : ChatEvent()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
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

    init {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.value = _uiState.value.copy(currentUserId = user?.id ?: "")
            loadMessages()
            startRealtimeUpdates()
            markAsRead()
        }
    }

    private suspend fun loadMessages() {
        if (conversationId.isBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true)
        when (val result = chatRepository.getMessages(conversationId)) {
            is Resource.Success -> {
                _uiState.value = _uiState.value.copy(
                    messages = result.data,
                    isLoading = false
                )
                if (result.data.isNotEmpty()) {
                    _events.emit(ChatEvent.ScrollToBottom)
                }
            }
            is Resource.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message
                )
            }
            is Resource.Loading -> {}
        }
    }

    private fun startRealtimeUpdates() {
        if (conversationId.isBlank()) return
        realtimeJob = viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collect { messages ->
                // Realtime/DB is authoritative — but keep any optimistic message
                // that hasn't been persisted/echoed yet so it never disappears.
                val serverIds = messages.map { it.id }.toSet()
                val pendingLocal = _uiState.value.messages.filter {
                    it.id.startsWith(LOCAL_PREFIX) && messages.none { m -> m.sameContent(it) }
                }
                _uiState.value = _uiState.value.copy(
                    messages = (messages + pendingLocal).sortedBy { it.createdAt },
                    isLoading = false
                )
                _events.emit(ChatEvent.ScrollToBottom)
                markAsRead()
            }
        }
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

        // 1. Optimistic update — show the message immediately with a local id so
        //    sending feels instant regardless of realtime/DB round-trip latency.
        val now = System.currentTimeMillis()
        val optimistic = Message(
            id             = "$LOCAL_PREFIX$now",
            conversationId = conversationId,
            senderId       = state.currentUserId,
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

        // 2. Persist. On success, swap the local copy for the server row; realtime
        //    will also reconcile. On failure, drop the optimistic message.
        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(optimistic.copy(id = ""))) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        messages = _uiState.value.messages
                            .map { if (it.id == optimistic.id) result.data else it }
                            .distinctBy { it.id }
                            .sortedBy { it.createdAt }
                    )
                }
                is Resource.Error -> {
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
    }

    private companion object {
        const val LOCAL_PREFIX = "local_"
    }
}
