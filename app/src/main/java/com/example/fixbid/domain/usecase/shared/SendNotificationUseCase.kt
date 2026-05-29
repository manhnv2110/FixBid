package com.example.fixbid.domain.usecase.shared

import com.example.fixbid.domain.model.NotificationContent
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.NotificationRepository
import javax.inject.Inject

/**
 * Persists a notification for a recipient. Trigger points (booking, bidding, job
 * lifecycle) call this after their primary action succeeds. Failures are
 * non-fatal to the caller — a missed notification must never roll back the main
 * operation — so callers typically ignore the result.
 */
class SendNotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(content: NotificationContent): Resource<Unit> =
        notificationRepository.createNotification(
            userId      = content.recipientUserId,
            title       = content.title,
            body        = content.body,
            type        = content.type,
            referenceId = content.referenceId
        )
}
