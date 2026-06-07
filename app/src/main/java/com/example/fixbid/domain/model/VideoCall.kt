package com.example.fixbid.domain.model

/**
 * Persisted state of one video call. Mirrors the `public.video_calls` row.
 *
 * The Jitsi Meet room name is derived from [id] — both peers compute the
 * same `fixbid-${id}` and join, no separate exchange step needed.
 *
 * The DB row is the single source of truth for ringing → accepted | rejected
 * | missed | ended transitions; clients subscribe to its UPDATE events via
 * Supabase Realtime and react locally (caller sees "đã chấp nhận" the moment
 * the callee taps Accept, etc.).
 */
data class VideoCall(
    val id: String,
    val conversationId: String,
    val callerId: String,
    val calleeId: String,
    val status: CallStatus,
    val startedAt: Long,
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val durationSeconds: Int? = null,
    /** Loaded eagerly when the screen needs caller name/avatar for the dialog. */
    val caller: User? = null,
    val callee: User? = null
) {
    /** Stable Jitsi room name. */
    val roomName: String get() = "fixbid-$id"
}

enum class CallStatus {
    /** Call just placed, callee not yet answered. */
    RINGING,

    /** Callee accepted; both peers should be inside the Jitsi room. */
    ACCEPTED,

    /** Callee tapped reject. */
    REJECTED,

    /** Callee never answered before timeout / caller cancel. */
    MISSED,

    /** Either side hung up cleanly. */
    ENDED;

    companion object {
        fun fromRaw(raw: String): CallStatus =
            runCatching { valueOf(raw.uppercase()) }.getOrDefault(ENDED)
    }

    val dbValue: String get() = name.lowercase()
}
