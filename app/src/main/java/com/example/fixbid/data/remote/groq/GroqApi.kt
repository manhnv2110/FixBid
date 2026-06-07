package com.example.fixbid.data.remote.groq

import com.example.fixbid.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thrown by [GroqApi] when the upstream returns HTTP 429. Carries the parsed
 * `retry-after` hint so the caller can sleep the right amount before retrying
 * (Groq also embeds the wait time in the error message body, e.g.
 * `"Please try again in 525ms"` — we parse both).
 */
class GroqRateLimitException(
    val retryAfterMs: Long,
    message: String? = null
) : Exception(message ?: "Rate limit hit; retry in ${retryAfterMs}ms")

/**
 * Thin Ktor client for Groq's OpenAI-compatible Chat Completions API.
 *
 * Supports both:
 *  - one-shot [chat] (full JSON body)
 *  - streaming [chatStream] (Server-Sent Events; emits one [GroqStreamChunk]
 *    per `data:` line, finishing on the `[DONE]` sentinel)
 *
 * On HTTP 429 both entry points raise [GroqRateLimitException] with a parsed
 * retry-after hint instead of bubbling a generic Ktor `ClientRequestException`.
 *
 * The API key is read from BuildConfig (provided via local.properties).
 */
@Singleton
class GroqApi @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = HttpClient(OkHttp) {
        // Disable Ktor's default 4xx auto-throw — we want to inspect the
        // response body / headers ourselves to extract the retry-after hint.
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            // Stream mode keeps the connection open for as long as the model
            // takes to finish — bump the request timeout accordingly.
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    suspend fun chat(request: GroqChatRequest): GroqChatResponse {
        val response: HttpResponse = client.post(ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer ${BuildConfig.GROQ_API_KEY}")
            contentType(ContentType.Application.Json)
            setBody(request.copy(stream = false))
        }
        if (response.status == HttpStatusCode.TooManyRequests) {
            throw response.toRateLimitException()
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Groq HTTP ${response.status.value}")
        }
        return response.body()
    }

    /**
     * Stream chat completions over SSE. Each emitted [GroqStreamChunk] is a
     * single token delta (or tool-call delta) — the caller is responsible for
     * concatenating the textual `delta.content` and the per-`index` tool-call
     * arguments to get the final shape.
     *
     * The stream completes when:
     *  - the `[DONE]` sentinel arrives, or
     *  - the body channel closes (connection ended), or
     *  - the collector cancels.
     *
     * Rate-limit responses bubble up as [GroqRateLimitException] before any
     * chunk is emitted; other network failures bubble up as exceptions.
     */
    fun chatStream(request: GroqChatRequest): Flow<GroqStreamChunk> = flow {
        client.preparePost(ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer ${BuildConfig.GROQ_API_KEY}")
            header(HttpHeaders.Accept, "text/event-stream")
            contentType(ContentType.Application.Json)
            setBody(request.copy(stream = true))
        }.execute { response ->
            if (response.status == HttpStatusCode.TooManyRequests) {
                throw response.toRateLimitException()
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Groq HTTP ${response.status.value}")
            }
            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val raw = channel.readUTF8Line() ?: break
                // SSE framing: `data: <json>` or `data: [DONE]`. Comments
                // (lines starting with ":") and empty separator lines are
                // ignored.
                if (raw.isBlank() || raw.startsWith(":")) continue
                val payload = raw.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                if (payload == "[DONE]") break
                runCatching { json.decodeFromString<GroqStreamChunk>(payload) }
                    .getOrNull()
                    ?.let { emit(it) }
            }
        }
    }

    /** Build a [GroqRateLimitException] from a 429 response. */
    private suspend fun HttpResponse.toRateLimitException(): GroqRateLimitException {
        val body = runCatching { bodyAsText() }.getOrDefault("")
        // Prefer the `retry-after` HTTP header (seconds, integer) when present.
        val headerSeconds = headers[HttpHeaders.RetryAfter]?.toIntOrNull()
        // Otherwise parse the embedded "try again in Xs/Xms" hint.
        val parsedFromBody = parseRetryAfterFromMessage(body)
        val retryMs = when {
            headerSeconds != null -> headerSeconds * 1_000L
            parsedFromBody != null -> parsedFromBody
            else -> DEFAULT_RETRY_MS
        }.coerceIn(MIN_RETRY_MS, MAX_RETRY_MS)
        return GroqRateLimitException(
            retryAfterMs = retryMs,
            message = body.take(240).ifBlank { "Groq rate limit" }
        )
    }

    /**
     * Pull the ms / s wait time out of a Groq 429 message body. Examples:
     *  - "Please try again in 525ms"
     *  - "Please try again in 1.34s"
     *  - "try again in 1m23.456s"
     */
    private fun parseRetryAfterFromMessage(body: String): Long? {
        // Combined regex: <num><unit> — captures the FIRST occurrence after
        // "try again in" so we don't accidentally pick up "12000" from the
        // "Limit 12000" prefix.
        val anchor = body.indexOf("try again in", ignoreCase = true)
        if (anchor < 0) return null
        val tail = body.substring(anchor)
        val msMatch = Regex("""(\d+(?:\.\d+)?)\s*ms""", RegexOption.IGNORE_CASE).find(tail)
        if (msMatch != null) {
            return msMatch.groupValues[1].toDoubleOrNull()?.toLong()
        }
        val combo = Regex(
            """(?:(\d+)\s*m)?\s*(\d+(?:\.\d+)?)\s*s""",
            RegexOption.IGNORE_CASE
        ).find(tail) ?: return null
        val minutes = combo.groupValues[1].toIntOrNull() ?: 0
        val seconds = combo.groupValues[2].toDoubleOrNull() ?: 0.0
        return ((minutes * 60.0 + seconds) * 1_000.0).toLong()
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

        /** High-quality default — slowest TPM bucket. */
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"

        /** Faster, larger TPM bucket — used as fallback when DEFAULT is rate-limited. */
        const val FALLBACK_MODEL = "llama-3.1-8b-instant"

        /** Conservative floor / ceiling for parsed retry-after. */
        private const val MIN_RETRY_MS = 200L
        private const val MAX_RETRY_MS = 30_000L
        private const val DEFAULT_RETRY_MS = 1_500L
    }
}
