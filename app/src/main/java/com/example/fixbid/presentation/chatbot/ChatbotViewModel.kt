package com.example.fixbid.presentation.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.ChatbotMessage
import com.example.fixbid.domain.model.ChatbotRole
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.domain.repository.AiAgentRepository
import com.example.fixbid.domain.repository.AiHistoryTurn
import com.example.fixbid.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatbotUiState(
    val messages: List<ChatbotMessage> = emptyList(),
    val input: String = "",
    val isThinking: Boolean = false
)

sealed interface ChatbotNavEvent {
    data class Navigate(val route: String) : ChatbotNavEvent
}

@HiltViewModel
class ChatbotViewModel @Inject constructor(
    private val aiAgentRepository: AiAgentRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatbotUiState())
    val uiState: StateFlow<ChatbotUiState> = _uiState.asStateFlow()

    private val _navEvents = Channel<ChatbotNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    private var userRole: UserRole = UserRole.CUSTOMER

    /** Quick-suggestion chips shown when the conversation is empty. */
    val suggestions = listOf(
        "Tìm thợ vệ sinh nhà",
        "Đơn của tôi đang thế nào?",
        "Gợi ý thợ điện nước uy tín",
        "Hướng dẫn đặt dịch vụ"
    )

    init {
        viewModelScope.launch {
            userRole = authRepository.getCurrentUser()?.role ?: UserRole.CUSTOMER
            // Greeting message.
            _uiState.update {
                it.copy(
                    messages = listOf(
                        ChatbotMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatbotRole.ASSISTANT,
                            text = "Xin chào! Mình là trợ lý FixBid. Mình có thể giúp bạn tìm thợ, " +
                                "kiểm tra đơn dịch vụ và hướng dẫn sử dụng app. Bạn cần gì nào?"
                        )
                    )
                )
            }
        }
    }

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value) }

    fun sendSuggestion(text: String) {
        _uiState.update { it.copy(input = text) }
        send()
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isBlank() || _uiState.value.isThinking) return

        val userMsg = ChatbotMessage(
            id = UUID.randomUUID().toString(),
            role = ChatbotRole.USER,
            text = text
        )
        val history = _uiState.value.messages.map {
            AiHistoryTurn(isUser = it.role == ChatbotRole.USER, text = it.text)
        }
        _uiState.update {
            it.copy(messages = it.messages + userMsg, input = "", isThinking = true)
        }

        viewModelScope.launch {
            when (val result = aiAgentRepository.sendMessage(text, history, userRole)) {
                is Resource.Success -> {
                    val reply = result.data
                    _uiState.update {
                        it.copy(
                            isThinking = false,
                            messages = it.messages + ChatbotMessage(
                                id = UUID.randomUUID().toString(),
                                role = ChatbotRole.ASSISTANT,
                                text = reply.text,
                                navigationRoute = reply.navigationRoute,
                                pendingAction = reply.pendingAction
                            )
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isThinking = false,
                            messages = it.messages + ChatbotMessage(
                                id = UUID.randomUUID().toString(),
                                role = ChatbotRole.ASSISTANT,
                                text = "Xin lỗi, mình gặp sự cố: ${result.message}. Bạn thử lại nhé.",
                                isError = true
                            )
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /** User confirmed a pending action (cancel/review/bid) → execute it. */
    fun confirmAction(messageId: String, action: com.example.fixbid.domain.repository.AiPendingAction) {
        markActionResolved(messageId)
        _uiState.update { it.copy(isThinking = true) }
        viewModelScope.launch {
            when (val result = aiAgentRepository.confirmAction(action, userRole)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isThinking = false,
                        messages = it.messages + ChatbotMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatbotRole.ASSISTANT,
                            text = result.data.text
                        )
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isThinking = false,
                        messages = it.messages + ChatbotMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatbotRole.ASSISTANT,
                            text = "Không thực hiện được: ${result.message}",
                            isError = true
                        )
                    )
                }
                is Resource.Loading -> {}
            }
        }
    }

    /** User dismissed a pending action. */
    fun dismissAction(messageId: String) {
        markActionResolved(messageId)
        _uiState.update {
            it.copy(
                messages = it.messages + ChatbotMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatbotRole.ASSISTANT,
                    text = "Đã huỷ thao tác. Bạn cần mình giúp gì thêm không?"
                )
            )
        }
    }

    private fun markActionResolved(messageId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(actionResolved = true) else it
                }
            )
        }
    }

    fun onNavigationClick(route: String) {
        viewModelScope.launch { _navEvents.send(ChatbotNavEvent.Navigate(route)) }
    }
}
