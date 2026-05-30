package com.example.fixbid.data.remote.supabase

import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Activates this realtime [RealtimeChannel] for the lifetime of the returned flow.
 *
 * supabase-kt builds a `postgresChangeFlow` lazily but it only emits **after**
 * `channel.subscribe()` is called. Forgetting to subscribe is why realtime
 * appeared "broken" (updates only showed after a manual reload, which re-ran the
 * one-time fetch). This helper subscribes when collection starts and unsubscribes
 * when it stops, so every `observe*` flow becomes truly live with one call.
 */
fun <T> RealtimeChannel.liveFlow(changeFlow: Flow<T>): Flow<T> =
    changeFlow
        .onStart { subscribe() }
        .onCompletion { runCatching { unsubscribe() } }
