package com.example.fixbid.presentation.ai

import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.AiSuggestion
import com.example.fixbid.domain.model.AiSuggestionKind
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.domain.repository.AiAgentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Reusable bridge between a screen-level ViewModel and the AI suggestion UI.
 *
 * Hosting ViewModels create one instance per screen and forward
 * [onSuggestionTapped] from the strip — the controller decides whether to:
 *   - Run an inline analysis ([AiSuggestionKind.INLINE_ANALYZE]) → updates the
 *     [inlineState] Flow which drives [InlineAiAnalysisCard].
 *   - Emit a "pre-fill chat" intent ([AiSuggestionKind.PREFILL_CHAT]) →
 *     populates [pendingChatPrefill] which the screen consumes and turns
 *     into a navController.navigate("chatbot?prefill=…") call.
 *   - Emit a navigation route ([AiSuggestionKind.NAVIGATE]) → populates
 *     [pendingRoute] consumed the same way.
 *
 * The controller is intentionally minimal — no Hilt injection itself; the
 * hosting VM provides the agent repository + a [CoroutineScope] (typically
 * `viewModelScope`).
 */
class AiSuggestionController(
    private val scope: CoroutineScope,
    private val aiAgentRepository: AiAgentRepository,
    private val role: UserRole
) {

    private val _inlineState = MutableStateFlow<InlineAiState>(InlineAiState.Idle)
    val inlineState: StateFlow<InlineAiState> = _inlineState.asStateFlow()

    /** Last suggestion that ran an inline analysis — used to power retry. */
    private var lastAnalyzeSuggestion: AiSuggestion? = null
    private var inlineJob: Job? = null

    private val _pendingChatPrefill = MutableStateFlow<String?>(null)
    val pendingChatPrefill: StateFlow<String?> = _pendingChatPrefill.asStateFlow()

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    /** Single dispatch point — call from the AiSuggestionStrip onSuggestionClick. */
    fun onSuggestionTapped(suggestion: AiSuggestion) {
        when (suggestion.kind) {
            AiSuggestionKind.INLINE_ANALYZE -> runInline(suggestion)
            AiSuggestionKind.PREFILL_CHAT -> _pendingChatPrefill.value = suggestion.prompt
            AiSuggestionKind.NAVIGATE -> suggestion.route?.let { _pendingRoute.value = it }
        }
    }

    /** Re-run the most recent inline analysis (driven by the card's "Thử lại"). */
    fun retryInline() {
        lastAnalyzeSuggestion?.let { runInline(it) }
    }

    fun dismissInline() {
        inlineJob?.cancel()
        _inlineState.value = InlineAiState.Idle
    }

    /** "Hỏi thêm trong chat" CTA on the result card — open chat with the same prompt. */
    fun openInlineInChat() {
        val s = lastAnalyzeSuggestion ?: return
        _pendingChatPrefill.value = s.prompt
        // Don't dismiss the inline result automatically — the user might still
        // want to read it after returning from chat.
    }

    fun consumeChatPrefill(): String? {
        val v = _pendingChatPrefill.value
        if (v != null) _pendingChatPrefill.value = null
        return v
    }

    fun consumeRoute(): String? {
        val v = _pendingRoute.value
        if (v != null) _pendingRoute.value = null
        return v
    }

    /**
     * Convert a prefill prompt into the chatbot route. The screen calls this
     * while consuming [pendingChatPrefill] and feeds the result into
     * `navController.navigate(...)`.
     */
    fun chatRouteFor(prefill: String): String {
        val encoded = URLEncoder.encode(prefill, StandardCharsets.UTF_8.name())
        return "chatbot?prefill=$encoded"
    }

    private fun runInline(suggestion: AiSuggestion) {
        lastAnalyzeSuggestion = suggestion
        inlineJob?.cancel()
        _inlineState.value = InlineAiState.Loading()
        inlineJob = scope.launch {
            when (val res = aiAgentRepository.analyze(suggestion.prompt, role)) {
                is Resource.Success -> {
                    _inlineState.value = InlineAiState.Result(markdown = res.data)
                }
                is Resource.Error -> {
                    _inlineState.value = InlineAiState.Error(
                        message = res.message ?: "Không nhận được phản hồi từ trợ lý."
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }
}
