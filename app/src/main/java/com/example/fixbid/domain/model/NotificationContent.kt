package com.example.fixbid.domain.model

/**
 * A ready-to-send notification: who it's for, what it says, and how it's
 * classified. Produced by [NotificationContentFactory] so copy stays consistent
 * across every trigger point (booking, bidding, job lifecycle, …).
 */
data class NotificationContent(
    val recipientUserId: String,
    val title: String,
    val body: String,
    val type: NotificationType,
    val referenceId: String?
)
