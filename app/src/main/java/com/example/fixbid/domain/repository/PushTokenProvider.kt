package com.example.fixbid.domain.repository

/**
 * Abstracts retrieval of the device push token away from the concrete messaging
 * SDK (Firebase), so the domain layer and use cases stay vendor-neutral and the
 * app still compiles/runs when no push provider is configured.
 */
interface PushTokenProvider {
    /**
     * Returns the current device registration token, or `null` if push is not
     * available (provider not configured, Play Services missing, fetch failed).
     */
    suspend fun getToken(): String?
}
