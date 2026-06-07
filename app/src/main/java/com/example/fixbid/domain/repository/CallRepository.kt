package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.VideoCall
import kotlinx.coroutines.flow.Flow

/**
 * Operations against `public.video_calls`.
 *
 * The repository is intentionally minimal — Jitsi handles the actual media
 * pipeline, so all this layer does is:
 *   1. Insert a "ringing" row when the caller taps the call button.
 *   2. Stream every UPDATE on rows the current user is a party to so the
 *      caller knows when the callee accepts / rejects, and the callee gets
 *      a global-scope "incoming call" trigger.
 *   3. Expose accept / reject / end transitions used by the call screen.
 */
interface CallRepository {

    /** Insert a fresh ringing row, return the persisted [VideoCall]. */
    suspend fun startCall(
        conversationId: String,
        callerId: String,
        calleeId: String
    ): Resource<VideoCall>

    /** Callee accepts an incoming ringing call. */
    suspend fun acceptCall(callId: String): Resource<VideoCall>

    /** Callee rejects, or the caller cancels before the callee answers. */
    suspend fun rejectCall(callId: String): Resource<VideoCall>

    /**
     * Either party hangs up cleanly after the call connected. The
     * `duration_seconds` is computed client-side from `answered_at` so the
     * value is consistent regardless of clock drift between the two peers.
     */
    suspend fun endCall(callId: String, durationSeconds: Int): Resource<VideoCall>

    /** One-time fetch (used after navigating into the call screen). */
    suspend fun getCall(callId: String): Resource<VideoCall>

    /** Realtime stream of any change to a specific call row. */
    fun observeCall(callId: String): Flow<VideoCall?>

    /**
     * Realtime stream of incoming ringing calls for [userId]. The global
     * "incoming call" dialog subscribes to this — emits every time a row
     * is inserted with `callee_id = userId AND status = 'ringing'`.
     */
    fun observeIncomingCalls(userId: String): Flow<VideoCall>
}
