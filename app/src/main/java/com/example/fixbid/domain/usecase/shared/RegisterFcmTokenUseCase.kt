package com.example.fixbid.domain.usecase.shared

import com.example.fixbid.data.local.UserPreferencesDataStore
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.NotificationRepository
import com.example.fixbid.domain.repository.PushTokenProvider
import javax.inject.Inject

/**
 * Fetches the device push token and upserts it against the signed-in user so the
 * backend can target this device with FCM. Call after a session is established
 * (login / app start while authenticated).
 *
 * No-ops gracefully when push isn't configured (token is null) or no user is
 * signed in, so it's always safe to invoke.
 */
class RegisterFcmTokenUseCase @Inject constructor(
    private val pushTokenProvider: PushTokenProvider,
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
    private val preferences: UserPreferencesDataStore
) {
    suspend operator fun invoke() {
        val token = pushTokenProvider.getToken() ?: return
        val user = authRepository.getCurrentUser() ?: return
        runCatching {
            preferences.saveFcmToken(token)
            notificationRepository.saveFcmToken(user.id, token)
        }
    }
}
