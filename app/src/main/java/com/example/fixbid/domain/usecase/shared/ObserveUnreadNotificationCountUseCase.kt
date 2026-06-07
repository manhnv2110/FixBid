package com.example.fixbid.domain.usecase.shared

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * Emits the current unread notification count for the signed-in user, then
 * re-emits whenever a new notification arrives over realtime. Powers the unread
 * badge on the notification bell.
 */
class ObserveUnreadNotificationCountUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<Int> = flow {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            emit(0)
            return@flow
        }

        suspend fun currentCount(): Int =
            (notificationRepository.getUnreadCount(user.id) as? Resource.Success)?.data ?: 0

        // Seed with the current count, then recompute on every notification change (insert, update, delete).
        emitAll(
            notificationRepository.observeNotifications(user.id)
                .map { currentCount() }
                .onStart { emit(currentCount()) }
        )
    }
}
