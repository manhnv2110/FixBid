package com.example.fixbid.data.remote

import com.example.fixbid.domain.repository.PushTokenProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * [PushTokenProvider] backed by Firebase Cloud Messaging.
 *
 * `FirebaseMessaging.getInstance()` is safe to reference without
 * `google-services.json`; the token fetch simply fails and we return `null`,
 * so the app degrades to in-app realtime notifications only.
 */
class FirebasePushTokenProvider @Inject constructor() : PushTokenProvider {
    override suspend fun getToken(): String? =
        runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
}
