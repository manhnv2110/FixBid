package com.example.fixbid.data.repository

import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny in-memory cache for AI tool results, keyed by `(toolName, argsJson)`.
 *
 * Why: the LLM very often calls the same read tool multiple times in a single
 * conversation (e.g. `get_my_bookings` first to list, then again to look up
 * an id). Caching the JSON output for [TTL_MILLIS] cuts both Groq token usage
 * (no need to reseed the same payload through the model) and Supabase load.
 *
 * Cache rules:
 *  - Only **read** tools should be cached. Action tools must always run live —
 *    [AiToolExecutor.shouldCache] tells [AiToolExecutor] which to hit.
 *  - TTL is short (30 s) so the conversation still feels live: a wallet
 *    snapshot from 30 s ago is fine, but we don't want a stale answer 5 min
 *    later if the user actively refreshes.
 *  - Hard cap of [MAX_ENTRIES] — LRU eviction by access time.
 *  - Process-local (no DataStore) — clearing happens automatically when the
 *    app is killed; we don't need durability.
 */
@Singleton
class AiToolCache @Inject constructor() {

    private data class Entry(
        val resultJson: String,
        val navigationRoute: String?,
        val storedAt: Long,
        var lastAccessedAt: Long
    )

    private val map = LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true)

    @Synchronized
    fun get(toolName: String, args: JsonObject): ToolRunResult? {
        val key = key(toolName, args)
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() - entry.storedAt > TTL_MILLIS) {
            map.remove(key)
            return null
        }
        entry.lastAccessedAt = System.currentTimeMillis()
        return ToolRunResult(entry.resultJson, entry.navigationRoute)
    }

    @Synchronized
    fun put(toolName: String, args: JsonObject, result: ToolRunResult) {
        // Don't cache failure results — the next call might succeed and we
        // shouldn't keep returning a stale error.
        if (result.resultJson.contains(""""error":""")) return
        val key = key(toolName, args)
        val now = System.currentTimeMillis()
        map[key] = Entry(
            resultJson = result.resultJson,
            navigationRoute = result.navigationRoute,
            storedAt = now,
            lastAccessedAt = now
        )
        // Evict oldest entries past the cap (LinkedHashMap accessOrder=true
        // already moves accessed entries to the tail, so the head is LRU).
        while (map.size > MAX_ENTRIES) {
            val it = map.entries.iterator()
            if (it.hasNext()) { it.next(); it.remove() } else break
        }
    }

    @Synchronized
    fun invalidate() = map.clear()

    private fun key(toolName: String, args: JsonObject) =
        "$toolName::${args.toString().hashCode()}"

    private companion object {
        const val TTL_MILLIS: Long = 30_000L
        const val MAX_ENTRIES = 32
    }
}
