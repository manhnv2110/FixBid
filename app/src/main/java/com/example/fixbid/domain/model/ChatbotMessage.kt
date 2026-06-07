package com.example.fixbid.domain.model

import com.example.fixbid.domain.repository.AiPendingAction

/** A message shown in the AI assistant conversation. */
data class ChatbotMessage(
    val id: String,
    val role: ChatbotRole,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    /** Optional in-app navigation suggested by the assistant (open_screen tool). */
    val navigationRoute: String? = null,
    /** A write/destructive action awaiting the user's confirmation, if any. */
    val pendingAction: AiPendingAction? = null,
    /** Set true once a pending action has been confirmed or dismissed. */
    val actionResolved: Boolean = false,
    /**
     * True while the assistant is still streaming this bubble's text. The UI
     * uses this to render a caret (▌) and to disable the per-message action
     * buttons until the stream finishes. Always false on persisted messages.
     */
    val isStreaming: Boolean = false,
    /**
     * Live tool-execution progress. Only populated on the assistant's current
     * "in flight" message and rendered as small "🔧 Đang …" progress chips
     * in the bubble. Cleared when [isStreaming] becomes false.
     */
    val toolProgress: List<ToolProgress> = emptyList()
)

enum class ChatbotRole { USER, ASSISTANT }

/** Per-tool progress entry rendered inside a streaming assistant bubble. */
data class ToolProgress(
    val toolName: String,
    val displayName: String,
    val finished: Boolean = false,
    val success: Boolean = true
)
