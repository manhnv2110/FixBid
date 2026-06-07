package com.example.fixbid.presentation.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.data.local.ChatbotHistoryDataStore
import com.example.fixbid.domain.model.ChatbotMessage
import com.example.fixbid.domain.model.ChatbotRole
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ToolProgress
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.domain.repository.AiAgentRepository
import com.example.fixbid.domain.repository.AiHistoryTurn
import com.example.fixbid.domain.repository.AiPendingAction
import com.example.fixbid.domain.repository.AiStreamEvent
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.ProactivePrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val isThinking: Boolean = false,
    /** Role-aware suggestion chips shown when the conversation is empty. */
    val suggestions: List<String> = emptyList(),
    /** Latest proactive prompt the agent surfaces (highest-priority chip). */
    val proactivePrompt: ProactivePrompt? = null
)

sealed interface ChatbotNavEvent {
    data class Navigate(val route: String) : ChatbotNavEvent
}

/**
 * Drives the AI chatbot screen.
 *
 * Responsibilities:
 *  - Maintain the visible message list (streaming aware — partial text grows
 *    in place as deltas arrive).
 *  - Hydrate from persistent storage on first composition; persist after
 *    every assistant turn finishes.
 *  - Surface proactive prompts (from realtime notifications) as a chip the
 *    user can tap to forward to the agent.
 *
 * Heavy lifting (LLM call, tool execution, action validation) lives in
 * [AiAgentRepository]. This class is just plumbing.
 */
@HiltViewModel
class ChatbotViewModel @Inject constructor(
    private val aiAgentRepository: AiAgentRepository,
    private val authRepository: AuthRepository,
    private val chatHistoryStore: ChatbotHistoryDataStore,
    private val proactiveAssistant: com.example.fixbid.data.repository.ProactiveAssistantCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatbotUiState())
    val uiState: StateFlow<ChatbotUiState> = _uiState.asStateFlow()

    private val _navEvents = Channel<ChatbotNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    private var userRole: UserRole = UserRole.CUSTOMER
    private var userId: String = ""
    private var streamJob: Job? = null

    /** Backwards-compat alias used by the screen. */
    val suggestions: List<String> get() = _uiState.value.suggestions

    init {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            userRole = user?.role ?: UserRole.CUSTOMER
            userId = user?.id.orEmpty()
            hydrateOrSeed()
            observeProactivePrompts()
        }
    }

    // ── Bootstrap ──────────────────────────────────────────────────────────

    private suspend fun hydrateOrSeed() {
        val persisted = if (userId.isNotBlank()) chatHistoryStore.load(userId) else emptyList()
        if (persisted.isNotEmpty()) {
            _uiState.update {
                it.copy(messages = persisted, suggestions = roleSuggestions())
            }
        } else {
            seedConversation()
        }
    }

    private fun seedConversation() {
        val greeting = when (userRole) {
            UserRole.WORKER ->
                "Xin chào! Mình là **trợ lý FixBid**. Mình có thể giúp bạn xem yêu cầu mở, đặt " +
                    "báo giá, theo dõi đơn đang nhận, kiểm tra ví và thống kê thu nhập. _Bạn cần gì?_"
            UserRole.CUSTOMER ->
                "Xin chào! Mình là **trợ lý FixBid**. Mình có thể giúp bạn tìm thợ, đặt dịch vụ, " +
                    "kiểm tra đơn, xem ví và đánh giá sau khi xong việc. _Bạn cần gì?_"
        }
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatbotMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatbotRole.ASSISTANT,
                        text = greeting
                    )
                ),
                suggestions = roleSuggestions()
            )
        }
    }

    private fun roleSuggestions(): List<String> = when (userRole) {
        UserRole.WORKER -> listOf(
            "Có yêu cầu nào mới?",
            "Thu nhập tháng này",
            "Đơn đặt trực tiếp đang chờ",
            "Báo giá tôi đã gửi",
            "Số dư ví của tôi"
        )
        UserRole.CUSTOMER -> listOf(
            "Tìm thợ vệ sinh nhà",
            "Đơn của tôi đang thế nào?",
            "Tạo đơn sửa điện tại nhà",
            "Số dư ví của tôi",
            "Mở trang tìm thợ"
        )
    }

    private fun observeProactivePrompts() {
        viewModelScope.launch {
            proactiveAssistant.prompts.collect { p ->
                // Only stale prompts (older than 10 min) are ignored — the
                // SharedFlow's `replay=1` means an "old" suggestion would
                // otherwise jump in if the user opens the chatbot much later.
                if (System.currentTimeMillis() - p.createdAt > STALE_PROMPT_MS) return@collect
                _uiState.update { it.copy(proactivePrompt = p) }
            }
        }
    }

    // ── Public API used by the screen ──────────────────────────────────────

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value) }

    fun sendSuggestion(text: String) {
        _uiState.update { it.copy(input = text) }
        send()
    }

    /** Tap on the proactive chip → forward its `suggestion` text to the agent. */
    fun acceptProactivePrompt() {
        val prompt = _uiState.value.proactivePrompt ?: return
        _uiState.update { it.copy(proactivePrompt = null) }
        sendSuggestion(prompt.suggestion)
    }

    /** Dismiss the proactive chip without sending anything. */
    fun dismissProactivePrompt() {
        _uiState.update { it.copy(proactivePrompt = null) }
    }

    /** Wipe the conversation, drop the persisted blob, and reseed. */
    fun resetConversation() {
        if (_uiState.value.isThinking) return
        viewModelScope.launch {
            if (userId.isNotBlank()) chatHistoryStore.clear(userId)
            _uiState.update { ChatbotUiState() }
            seedConversation()
        }
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isBlank() || _uiState.value.isThinking) return

        val userMsg = ChatbotMessage(
            id = UUID.randomUUID().toString(),
            role = ChatbotRole.USER,
            text = text
        )
        // Capture history BEFORE appending the new user message so the agent
        // doesn't see it twice (it's also passed via `userMessage`).
        val history = _uiState.value.messages.map {
            AiHistoryTurn(isUser = it.role == ChatbotRole.USER, text = it.text)
        }

        // Reserve an empty assistant bubble that the streaming job will fill.
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantPlaceholder = ChatbotMessage(
            id = assistantMsgId,
            role = ChatbotRole.ASSISTANT,
            text = "",
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + assistantPlaceholder,
                input = "",
                isThinking = true
            )
        }

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            collectStream(text, history, assistantMsgId)
        }
    }

    private suspend fun collectStream(
        userText: String,
        history: List<AiHistoryTurn>,
        assistantMsgId: String
    ) {
        try {
            aiAgentRepository.sendMessageStream(userText, history, userRole).collect { event ->
                when (event) {
                    is AiStreamEvent.Planning -> {
                        // No-op: the placeholder bubble already shows the spinner.
                    }
                    is AiStreamEvent.ToolStart -> updateAssistant(assistantMsgId) { msg ->
                        msg.copy(
                            toolProgress = msg.toolProgress + ToolProgress(
                                toolName = event.name,
                                displayName = event.description
                            )
                        )
                    }
                    is AiStreamEvent.ToolEnd -> updateAssistant(assistantMsgId) { msg ->
                        val updatedProgress = msg.toolProgress.map { tp ->
                            if (tp.toolName == event.name && !tp.finished) {
                                tp.copy(finished = true, success = event.success)
                            } else tp
                        }
                        msg.copy(toolProgress = updatedProgress)
                    }
                    is AiStreamEvent.Delta -> updateAssistant(assistantMsgId) { msg ->
                        msg.copy(text = msg.text + event.text)
                    }
                    is AiStreamEvent.Final -> {
                        finalizeAssistant(assistantMsgId, event)
                        persistConversation()
                    }
                    is AiStreamEvent.Failure -> {
                        updateAssistant(assistantMsgId) { msg ->
                            msg.copy(
                                text = "Xin lỗi, mình gặp sự cố: ${event.message}. Bạn thử lại nhé.",
                                isStreaming = false,
                                toolProgress = emptyList(),
                                isError = true
                            )
                        }
                        _uiState.update { it.copy(isThinking = false) }
                        persistConversation()
                    }
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Job cancelled (e.g. screen closed) — just clean up the bubble.
            updateAssistant(assistantMsgId) { msg ->
                if (msg.isStreaming) {
                    msg.copy(
                        text = msg.text.ifBlank { "(đã huỷ)" },
                        isStreaming = false,
                        toolProgress = emptyList()
                    )
                } else msg
            }
            _uiState.update { it.copy(isThinking = false) }
            throw ce
        } catch (e: Exception) {
            updateAssistant(assistantMsgId) { msg ->
                msg.copy(
                    text = "Mình gặp sự cố không mong muốn. Bạn thử lại sau giây lát nhé.",
                    isStreaming = false,
                    toolProgress = emptyList(),
                    isError = true
                )
            }
            _uiState.update { it.copy(isThinking = false) }
            persistConversation()
        }
    }

    private fun finalizeAssistant(assistantMsgId: String, event: AiStreamEvent.Final) {
        updateAssistant(assistantMsgId) { msg ->
            // Prefer the streamed text we already accumulated, fall back to the
            // Final event's text (the loop's "max rounds exhausted" path uses
            // the non-streaming summary which arrives only in Final).
            val finalText = msg.text.ifBlank { event.reply.text }
            msg.copy(
                text = finalText,
                isStreaming = false,
                toolProgress = emptyList(),
                navigationRoute = event.reply.navigationRoute,
                pendingAction = event.reply.pendingAction
            )
        }
        _uiState.update { it.copy(isThinking = false) }
    }

    private fun updateAssistant(id: String, transform: (ChatbotMessage) -> ChatbotMessage) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { if (it.id == id) transform(it) else it }
            )
        }
    }

    private fun persistConversation() {
        if (userId.isBlank()) return
        val snapshot = _uiState.value.messages
        viewModelScope.launch {
            chatHistoryStore.save(userId, snapshot)
        }
    }

    // ── Action confirmation ────────────────────────────────────────────────

    fun confirmAction(messageId: String, action: AiPendingAction) {
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
                            text = result.data.text,
                            navigationRoute = result.data.navigationRoute
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
            persistConversation()
        }
    }

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
        persistConversation()
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

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }

    private companion object {
        const val STALE_PROMPT_MS = 10L * 60 * 1000   // 10 minutes
    }
}
