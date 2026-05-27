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
                _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
                _events.emit(ChatEvent.ScrollToBottom)
                markAsRead()
            }
        }
    }

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

        _uiState.value = state.copy(inputText = "", isSending = true)

        viewModelScope.launch {
            val message = Message(
                id             = "",
                conversationId = conversationId,
                senderId       = state.currentUserId,
                content        = text,
                type           = MessageType.TEXT,
                imageUrl       = null,
                isRead         = false,
                createdAt      = System.currentTimeMillis()
            )
            when (val result = chatRepository.sendMessage(message)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSending = false)
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
}
