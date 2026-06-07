package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.NotificationType

/**
 * A short, optional prompt the proactive assistant surfaces in the chatbot UI
 * when something interesting just happened (e.g. a bid arrived, the worker is
 * on the way). The user can either tap the prompt to send it as a message, or
 * dismiss it.
 *
 * The coordinator emits at most one prompt per real notification and ignores
 * types that aren't actionable inside the chatbot (system, cancelled, etc.).
 */
data class ProactivePrompt(
    /** Stable id — shared with the originating Notification so we can dedupe. */
    val id: String,
    val title: String,
    val body: String,
    /** The exact text that will be sent to the agent if the user taps the chip. */
    val suggestion: String,
    val sourceType: NotificationType,
    val createdAt: Long
)
