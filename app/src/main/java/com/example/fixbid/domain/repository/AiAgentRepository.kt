package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.UserRole

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

interface AiAgentRepository {
    /**
     * Sends [userMessage] (with prior [history]) to the LLM, runs any read tool
     * calls it requests, and returns the final reply. If the model wants to run a
     * write/destructive action, the reply carries [AiReply.pendingAction] instead
     * of executing it — the UI must confirm first.
     */
    suspend fun sendMessage(
        userMessage: String,
        history: List<AiHistoryTurn>,
        role: UserRole
    ): Resource<AiReply>

    /** Runs a previously-confirmed action tool and returns a short result message. */
    suspend fun confirmAction(action: AiPendingAction, role: UserRole): Resource<AiReply>
}
