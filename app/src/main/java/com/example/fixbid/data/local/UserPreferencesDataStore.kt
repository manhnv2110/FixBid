package com.example.fixbid.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fixbid.domain.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("user_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_ROLE          = stringPreferencesKey("user_role")
        val KEY_USER_ID       = stringPreferencesKey("user_id")
        val KEY_FCM_TOKEN     = stringPreferencesKey("fcm_token")
        val KEY_THEME         = stringPreferencesKey("app_theme")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val KEY_NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
        val KEY_NOTIF_SOUND   = booleanPreferencesKey("notif_sound")
        val KEY_NOTIF_VIBRATE = booleanPreferencesKey("notif_vibrate")
        val KEY_VERIFY_SUBMITTED_AT = stringPreferencesKey("verify_submitted_at")
    }

    val userRole: Flow<UserRole?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ROLE]?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
    }

    val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }

    val appTheme: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "system" }

    /**
     * Material You "dynamic color" preference. Defaults to **false** so the
     * app keeps its branded palette out of the box; users opt in from the
     * Profile menu. Only honored on Android 12+ (API 31) regardless of this
     * flag — see [com.example.fixbid.ui.theme.FixBidTheme] for the gate.
     */
    val dynamicColorEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: false }

    // ─── Notification preferences (default: all on) ──────────────────────────
    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_NOTIF_ENABLED] ?: true }

    val notificationSoundEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_NOTIF_SOUND] ?: true }

    val notificationVibrateEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_NOTIF_VIBRATE] ?: true }

    /**
     * Timestamp (epoch millis as string) of the last identity verification
     * submission, or `null` if the worker hasn't submitted yet. Stored
     * locally because the backend has no submission endpoint yet — used to
     * show a persistent "đang xét duyệt" state on the verify screen.
     */
    val verificationSubmittedAt: Flow<Long?> =
        context.dataStore.data.map { it[KEY_VERIFY_SUBMITTED_AT]?.toLongOrNull() }

    suspend fun saveUserSession(userId: String, role: UserRole) {
        context.dataStore.edit {
            it[KEY_USER_ID] = userId
            it[KEY_ROLE]    = role.name
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { it[KEY_FCM_TOKEN] = token }
    }

    suspend fun saveTheme(theme: String) {
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_ENABLED] = enabled }
    }

    suspend fun setNotificationSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_SOUND] = enabled }
    }

    suspend fun setNotificationVibrateEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_VIBRATE] = enabled }
    }

    suspend fun markVerificationSubmitted(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[KEY_VERIFY_SUBMITTED_AT] = timestamp.toString() }
    }

    suspend fun clearVerificationSubmission() {
        context.dataStore.edit { it.remove(KEY_VERIFY_SUBMITTED_AT) }
    }
}