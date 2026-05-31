package com.example.fixbid.data.remote.groq

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Groq Chat Completions DTOs. Groq exposes an OpenAI-compatible API, so these
 * mirror the OpenAI `/chat/completions` request/response shapes including tool
 * (function) calling.
 */

@Serializable
data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val tools: List<GroqTool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val temperature: Double = 0.3,
    @SerialName("max_tokens") val maxTokens: Int = 1024
)

@Serializable
data class GroqMessage(
    val role: String,                                  // system | user | assistant | tool
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<GroqToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null                           // for role=tool, the function name
)

@Serializable
data class GroqToolCall(
    val id: String = "",
    val type: String = "function",
    val function: GroqFunctionCall
)

@Serializable
data class GroqFunctionCall(
    val name: String = "",
    val arguments: String = ""                         // JSON string of args
)

@Serializable
data class GroqTool(
    val type: String = "function",
    val function: GroqFunctionDef
)

@Serializable
data class GroqFunctionDef(
    val name: String,
    val description: String,
    val parameters: JsonObject                          // JSON schema
)

@Serializable
data class GroqChatResponse(
    val choices: List<GroqChoice> = emptyList()
)

@Serializable
data class GroqChoice(
    val message: GroqResponseMessage = GroqResponseMessage(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class GroqResponseMessage(
    val role: String = "assistant",
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<GroqToolCall>? = null
)
