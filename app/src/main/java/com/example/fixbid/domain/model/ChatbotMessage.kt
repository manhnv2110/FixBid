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
    val actionResolved: Boolean = false
)

enum class ChatbotRole { USER, ASSISTANT }
