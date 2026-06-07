package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * An action the assistant wants to perform but that requires the user's explicit
 * confirmation before running (e.g. cancel a booking, submit a review, place a bid).
 */
data class AiPendingAction(
    val toolName: String,
    val argsJson: String,
    val title: String,         // short confirm title, e.g. "Hủy đơn?"
    val summary: String        // human-readable summary of what will happen
)

/** One turn of the assistant conversation, ready to render. */
data class AiReply(
    val text: String,
    val navigationRoute: String? = null,
    val pendingAction: AiPendingAction? = null
)

/** A prior turn passed back so the model keeps context across messages. */
data class AiHistoryTurn(
    val isUser: Boolean,
    val text: String
)

/**
 * Incremental event emitted while the agent runs.
 *
 * The chatbot UI consumes this stream and renders progress in real time:
 *   - [Planning]: agent acknowledged the user message, about to call the LLM.
 *   - [ToolStart]: an LLM-requested tool is about to execute.
 *   - [ToolEnd]: the tool finished (success or failure).
 *   - [Delta]: a chunk of streamed model output (markdown text).
 *   - [Final]: terminal event with the full reply (text + optional navigation
 *     route + optional pending-action card).
 *   - [Failure]: terminal event for a fatal error (network / invalid response).
 *
 * Producers MUST emit exactly one terminal event ([Final] or [Failure]) per
 * conversation turn.
 */
sealed interface AiStreamEvent {
    /** Agent received the user message and is contacting the model. */
    data object Planning : AiStreamEvent

    /** A tool call was issued by the model. */
    data class ToolStart(val name: String, val description: String) : AiStreamEvent

    /** A tool call finished. */
    data class ToolEnd(val name: String, val success: Boolean) : AiStreamEvent

    /** A streamed chunk of model text (markdown). */
    data class Delta(val text: String) : AiStreamEvent

    /** Terminal: the whole reply is ready. */
    data class Final(val reply: AiReply) : AiStreamEvent

    /** Terminal: agent failed with a user-facing message. */
    data class Failure(val message: String) : AiStreamEvent
}

interface AiAgentRepository {
    /**
     * One-shot, non-streaming entry point. Kept for callers that don't care
     * about progress events (e.g. confirm-action completion). Internally
     * collects the streaming flow and returns the [Final] event.
     */
    suspend fun sendMessage(
        userMessage: String,
        history: List<AiHistoryTurn>,
        role: UserRole
    ): Resource<AiReply>

    /**
     * Streaming entry point. Emits incremental [AiStreamEvent]s as the agent
     * plans → calls tools → streams the final reply. The flow is cold and
     * cancellable — collecting it again starts a fresh conversation turn.
     */
    fun sendMessageStream(
        userMessage: String,
        history: List<AiHistoryTurn>,
        role: UserRole
    ): Flow<AiStreamEvent>

    /** Runs a previously-confirmed action tool and returns a short result message. */
    suspend fun confirmAction(action: AiPendingAction, role: UserRole): Resource<AiReply>
}
