package com.example.fixbid.data.remote.groq

import com.example.fixbid.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin Ktor client for Groq's OpenAI-compatible Chat Completions API.
 * The API key is read from BuildConfig (provided via local.properties).
 */
@Singleton
class GroqApi @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }

    suspend fun chat(request: GroqChatRequest): GroqChatResponse =
        client.post(ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer ${BuildConfig.GROQ_API_KEY}")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    }
}
