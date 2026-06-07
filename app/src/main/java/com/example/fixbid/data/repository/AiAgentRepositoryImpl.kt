package com.example.fixbid.data.repository

import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.data.remote.groq.AiToolRegistry
import com.example.fixbid.data.remote.groq.GroqApi
import com.example.fixbid.data.remote.groq.GroqChatRequest
import com.example.fixbid.data.remote.groq.GroqFunctionCall
import com.example.fixbid.data.remote.groq.GroqMessage
import com.example.fixbid.data.remote.groq.GroqRateLimitException
import com.example.fixbid.data.remote.groq.GroqStreamChunk
import com.example.fixbid.data.remote.groq.GroqStreamToolCall
import com.example.fixbid.data.remote.groq.GroqToolCall
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.domain.repository.AiAgentRepository
import com.example.fixbid.domain.repository.AiHistoryTurn
import com.example.fixbid.domain.repository.AiPendingAction
import com.example.fixbid.domain.repository.AiReply
import com.example.fixbid.domain.repository.AiStreamEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * The streaming AI agent loop.
 *
 * Rate-limit handling (Groq free tier is 12k TPM on `llama-3.3-70b-versatile`):
 *   1. On a 429 response we read the parsed `retry-after` from the
 *      [GroqRateLimitException] and sleep that many ms.
 *   2. We retry up to [MAX_RATE_LIMIT_RETRIES] times.
 *   3. If we're still throttled, we transparently switch to
 *      [GroqApi.FALLBACK_MODEL] (`llama-3.1-8b-instant` — same TPM bucket
 *      family but ~2.5× the limit) and retry once more.
 *   4. On final failure we surface a friendly Vietnamese message.
 *
 * Token frugality:
 *   - System prompt is short (no verbose tool list — the model already sees
 *     full schemas via the `tools` array).
 *   - History is capped at the last 6 turns (was 10).
 *   - Tool result JSON is truncated by [AiToolExecutor] to small payloads.
 *   - Tool result cache (see [AiToolCache]) avoids re-roundtripping the
 *     same payload through the model.
 *   - `max_tokens` is 768 (was 1024) — enough for any chatbot reply.
 */
class AiAgentRepositoryImpl @Inject constructor(
    private val groqApi: GroqApi,
    private val toolExecutor: AiToolExecutor
) : AiAgentRepository {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ── Public entry points ─────────────────────────────────────────────────

    override suspend fun sendMessage(
        userMessage: String,
        history: List<AiHistoryTurn>,
        role: UserRole
    ): Resource<AiReply> {
        return try {
            var terminal: AiStreamEvent? = null
            sendMessageStream(userMessage, history, role).collect { event ->
                if (event is AiStreamEvent.Final || event is AiStreamEvent.Failure) {
                    terminal = event
                }
            }
            when (val finalEvent = terminal) {
                is AiStreamEvent.Final -> Resource.Success(finalEvent.reply)
                is AiStreamEvent.Failure -> Resource.Error(finalEvent.message)
                else -> Resource.Error(GENERIC_ERROR)
            }
        } catch (e: Exception) {
            Resource.Error(GENERIC_ERROR)
        }
    }

    override fun sendMessageStream(
        userMessage: String,
        history: List<AiHistoryTurn>,
        role: UserRole
    ): Flow<AiStreamEvent> = flow {
        try {
            emit(AiStreamEvent.Planning)
            runConversation(userMessage, history, role) { emit(it) }
        } catch (e: GroqRateLimitException) {
            // After all retries + fallback model still throttled — give the
            // user something actionable instead of a stack trace.
            emit(AiStreamEvent.Failure(RATE_LIMIT_ERROR))
        } catch (e: Exception) {
            emit(AiStreamEvent.Failure(GENERIC_ERROR))
        }
    }

    override suspend fun confirmAction(action: AiPendingAction, role: UserRole): Resource<AiReply> {
        return try {
            val args = runCatching { json.parseToJsonElement(action.argsJson).jsonObject }
                .getOrElse { JsonObject(emptyMap()) }
            val result = toolExecutor.execute(action.toolName, args)
            val resultObj = runCatching { json.parseToJsonElement(result.resultJson).jsonObject }
                .getOrNull()
            val msg = friendlyActionResult(action.toolName, resultObj)
            Resource.Success(
                AiReply(text = msg, navigationRoute = result.navigationRoute)
            )
        } catch (e: Exception) {
            Resource.Success(AiReply(text = "Rất tiếc, mình chưa thực hiện được thao tác này. Bạn thử lại sau nhé."))
        }
    }

    // ── Streaming conversation loop ─────────────────────────────────────────

    private suspend fun runConversation(
        userMessage: String,
        history: List<AiHistoryTurn>,
        role: UserRole,
        emit: suspend (AiStreamEvent) -> Unit
    ) {
        val messages = mutableListOf<GroqMessage>()
        messages += GroqMessage(role = "system", content = systemPrompt(role))
        // Trimmed from 10 → 6 to stay under TPM. Older turns are discarded.
        history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            messages += GroqMessage(
                role = if (turn.isUser) "user" else "assistant",
                content = turn.text
            )
        }
        messages += GroqMessage(role = "user", content = userMessage)

        val tools = AiToolRegistry.toolsFor(role)
        var pendingNavigation: String? = null

        repeat(MAX_TOOL_ROUNDS) {
            val streamResult = streamOneRound(messages, tools, emit)

            // 1. Plain text → terminal.
            if (streamResult.toolCalls.isEmpty()) {
                emit(
                    AiStreamEvent.Final(
                        AiReply(
                            text = streamResult.content.trim()
                                .ifBlank { "Mình chưa rõ yêu cầu, bạn nói rõ hơn giúp mình nhé." },
                            navigationRoute = pendingNavigation
                        )
                    )
                )
                return
            }

            // 2. Action tool → validate then surface confirmation card.
            val actionCall = streamResult.toolCalls
                .firstOrNull { AiToolRegistry.isActionTool(it.function.name) }
            if (actionCall != null) {
                val args = parseArgs(actionCall.function)
                val validationError = validateActionArgs(actionCall.function.name, args)
                if (validationError == null) {
                    emit(
                        AiStreamEvent.Final(
                            AiReply(
                                text = pendingActionIntroText(actionCall.function.name),
                                navigationRoute = pendingNavigation,
                                pendingAction = buildPendingAction(actionCall.function.name, args)
                            )
                        )
                    )
                    return
                }
                messages += GroqMessage(
                    role = "assistant",
                    content = streamResult.content,
                    toolCalls = streamResult.toolCalls
                )
                for (call in streamResult.toolCalls) {
                    val resultJson = if (call.id == actionCall.id) {
                        """{"error":"${validationError.jsonEscape()}"}"""
                    } else {
                        runReadTool(call, emit).resultJson
                    }
                    messages += GroqMessage(
                        role = "tool",
                        toolCallId = call.id,
                        name = call.function.name,
                        content = resultJson
                    )
                }
                return@repeat
            }

            // 3. Pure read tools — execute then loop.
            messages += GroqMessage(
                role = "assistant",
                content = streamResult.content,
                toolCalls = streamResult.toolCalls
            )
            for (call in streamResult.toolCalls) {
                val result = runReadTool(call, emit)
                if (result.navigationRoute != null) pendingNavigation = result.navigationRoute
                messages += GroqMessage(
                    role = "tool",
                    toolCallId = call.id,
                    name = call.function.name,
                    content = result.resultJson
                )
            }
        }

        val finalResp = chatWithRetry(
            GroqChatRequest(model = GroqApi.DEFAULT_MODEL, messages = messages, maxTokens = MAX_TOKENS)
        )
        emit(
            AiStreamEvent.Final(
                AiReply(
                    text = finalResp.choices.firstOrNull()?.message?.content?.trim()
                        ?: "Mình đã xử lý xong yêu cầu của bạn.",
                    navigationRoute = pendingNavigation
                )
            )
        )
    }

    private data class StreamRoundResult(
        val content: String,
        val toolCalls: List<GroqToolCall>
    )

    private suspend fun streamOneRound(
        messages: List<GroqMessage>,
        tools: List<com.example.fixbid.data.remote.groq.GroqTool>,
        emit: suspend (AiStreamEvent) -> Unit
    ): StreamRoundResult {
        val request = GroqChatRequest(
            model = GroqApi.DEFAULT_MODEL,
            messages = messages,
            tools = tools,
            toolChoice = "auto",
            stream = true,
            maxTokens = MAX_TOKENS
        )

        val contentBuf = StringBuilder()
        val toolBuf = mutableMapOf<Int, ToolCallBuf>()

        // Try the streaming API with rate-limit retries; if every retry fails
        // we fall back to a non-streaming call (which goes through the same
        // retry+model-fallback logic).
        try {
            chatStreamWithRetry(request).collect { chunk ->
                val choice = chunk.choices.firstOrNull() ?: return@collect
                val delta = choice.delta
                delta.content?.let { piece ->
                    if (piece.isNotEmpty()) {
                        contentBuf.append(piece)
                        emit(AiStreamEvent.Delta(piece))
                    }
                }
                delta.toolCalls?.forEach { tc -> toolBuf.absorb(tc) }
            }
        } catch (rl: GroqRateLimitException) {
            throw rl
        } catch (e: Exception) {
            return runNonStreamingFallback(messages, tools)
        }

        return StreamRoundResult(
            content = contentBuf.toString(),
            toolCalls = toolBuf.entries
                .sortedBy { it.key }
                .map { it.value.toFinal() }
        )
    }

    private suspend fun runNonStreamingFallback(
        messages: List<GroqMessage>,
        tools: List<com.example.fixbid.data.remote.groq.GroqTool>
    ): StreamRoundResult {
        val response = chatWithRetry(
            GroqChatRequest(
                model = GroqApi.DEFAULT_MODEL,
                messages = messages,
                tools = tools,
                toolChoice = "auto",
                maxTokens = MAX_TOKENS
            )
        )
        val msg = response.choices.firstOrNull()?.message
        return StreamRoundResult(
            content = msg?.content.orEmpty(),
            toolCalls = msg?.toolCalls.orEmpty()
        )
    }

    // ── Rate-limit aware Groq calls ─────────────────────────────────────────

    /**
     * Wraps [GroqApi.chat] with retries on 429.
     *
     * Retry plan:
     *   - Up to [MAX_RATE_LIMIT_RETRIES] retries on the requested model,
     *     sleeping `retryAfterMs` (server hint, clamped 200ms..30s) between
     *     attempts.
     *   - On final failure, attempt one last call against [GroqApi.FALLBACK_MODEL].
     *
     * Non-429 failures bubble up immediately.
     */
    private suspend fun chatWithRetry(request: GroqChatRequest) =
        runWithRetry(request) { groqApi.chat(it) }

    /** Streaming counterpart of [chatWithRetry]. */
    private fun chatStreamWithRetry(request: GroqChatRequest): Flow<GroqStreamChunk> = flow {
        val flow = runWithRetry(request) { groqApi.chatStream(it) }
        flow.collect { emit(it) }
    }

    private suspend fun <T> runWithRetry(
        request: GroqChatRequest,
        call: suspend (GroqChatRequest) -> T
    ): T {
        var attempt = 0
        var lastError: GroqRateLimitException? = null
        while (attempt <= MAX_RATE_LIMIT_RETRIES) {
            try {
                return call(request)
            } catch (rl: GroqRateLimitException) {
                lastError = rl
                attempt += 1
                if (attempt > MAX_RATE_LIMIT_RETRIES) break
                val wait = (rl.retryAfterMs + (50..200).random()).coerceAtMost(5_000L)
                delay(wait)
            }
        }
        // Last resort: try the smaller / faster model. It has a separate
        // TPM bucket so it usually goes through even when 70b is throttled.
        try {
            return call(request.copy(model = GroqApi.FALLBACK_MODEL))
        } catch (rl: GroqRateLimitException) {
            throw lastError ?: rl
        }
    }

    /** Run a read tool and emit start/end progress events. */
    private suspend fun runReadTool(
        call: GroqToolCall,
        emit: suspend (AiStreamEvent) -> Unit
    ): ToolRunResult {
        emit(AiStreamEvent.ToolStart(call.function.name, toolDisplayName(call.function.name)))
        val result = runCatching {
            val args = parseArgs(call.function)
            toolExecutor.execute(call.function.name, args)
        }.getOrElse { ToolRunResult("""{"error":"Lỗi thực thi công cụ"}""") }
        val success = !result.resultJson.contains(""""error":""")
        emit(AiStreamEvent.ToolEnd(call.function.name, success))
        return result
    }

    // ── Tool-call streaming buffer ──────────────────────────────────────────

    private class ToolCallBuf(
        var id: String? = null,
        var type: String = "function",
        var name: String = "",
        val args: StringBuilder = StringBuilder()
    ) {
        fun toFinal(): GroqToolCall = GroqToolCall(
            id = id ?: "",
            type = type,
            function = GroqFunctionCall(name = name, arguments = args.toString())
        )
    }

    private fun MutableMap<Int, ToolCallBuf>.absorb(delta: GroqStreamToolCall) {
        val buf = getOrPut(delta.index) { ToolCallBuf() }
        delta.id?.let { buf.id = it }
        delta.type?.let { buf.type = it }
        delta.function?.name?.takeIf { it.isNotBlank() }?.let { buf.name = it }
        delta.function?.arguments?.let { buf.args.append(it) }
    }

    // ── Argument helpers ────────────────────────────────────────────────────

    private fun parseArgs(fn: GroqFunctionCall): JsonObject =
        runCatching { json.parseToJsonElement(fn.arguments).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }

    private fun JsonObject.s(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.d(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.b(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    private fun validateActionArgs(toolName: String, args: JsonObject): String? {
        val bookingIdTools = setOf(
            AiToolRegistry.CANCEL_BOOKING, AiToolRegistry.SUBMIT_REVIEW,
            AiToolRegistry.PLACE_BID, AiToolRegistry.ACCEPT_DIRECT_BOOKING,
            AiToolRegistry.DECLINE_DIRECT_BOOKING, AiToolRegistry.START_JOB,
            AiToolRegistry.COMPLETE_JOB, AiToolRegistry.CONFIRM_COMPLETION,
            AiToolRegistry.REJECT_COMPLETION
        )
        if (toolName in bookingIdTools) {
            val id = args.s("bookingId")
                ?: return "Thiếu bookingId. Hãy gọi get_my_bookings (hoặc get_open_requests / " +
                    "get_pending_direct_bookings tuỳ vai trò) để lấy đúng UUID."
            if (!UUID_REGEX.matches(id)) {
                return "bookingId '$id' không phải UUID. KHÔNG bịa hoặc dùng cụm từ. " +
                    "Gọi công cụ tra cứu để lấy UUID thật."
            }
        }
        if (toolName == AiToolRegistry.CREATE_DIRECT_BOOKING) {
            val workerId = args.s("workerId")
                ?: return "Thiếu workerId. Hãy gọi search_workers để lấy UUID của thợ."
            if (!UUID_REGEX.matches(workerId)) return "workerId không phải UUID hợp lệ."
        }
        if (toolName == AiToolRegistry.ACCEPT_BID) {
            val bidId = args.s("bidId")
                ?: return "Thiếu bidId. Hãy gọi get_bids_for_booking để lấy đúng UUID của báo giá."
            if (!UUID_REGEX.matches(bidId)) {
                return "bidId '$bidId' không phải UUID. Gọi get_bids_for_booking để lấy UUID thật."
            }
        }
        if (toolName == AiToolRegistry.SUBMIT_REVIEW) {
            val rating = args.d("rating")?.toInt()
            if (rating == null || rating !in 1..5) return "Số sao phải từ 1 đến 5."
        }
        if (toolName == AiToolRegistry.PLACE_BID) {
            val price = args.d("price")
            if (price == null || price <= 0) return "Giá báo phải lớn hơn 0."
        }
        if (toolName == AiToolRegistry.SET_AVAILABILITY) {
            args.b("isAvailable") ?: return "Thiếu trạng thái 'isAvailable' (true/false)."
        }
        if (toolName == AiToolRegistry.REJECT_COMPLETION ||
            toolName == AiToolRegistry.DECLINE_DIRECT_BOOKING
        ) {
            args.s("reason") ?: return "Thiếu lý do."
        }
        if (toolName == AiToolRegistry.CREATE_BOOKING || toolName == AiToolRegistry.CREATE_DIRECT_BOOKING) {
            if (args.s("category") == null) return "Thiếu danh mục dịch vụ (category)."
            if (args.s("description") == null) return "Thiếu mô tả công việc."
            if (args.s("address") == null) return "Thiếu địa chỉ."
            if (args.s("scheduledAt") == null && args["scheduledAt"] == null) {
                return "Thiếu thời gian (scheduledAt). Có thể là ISO-8601 hoặc epoch millis."
            }
        }
        return null
    }

    // ── Pending action UX ───────────────────────────────────────────────────

    private fun buildPendingAction(toolName: String, args: JsonObject): AiPendingAction {
        val argsJson = JsonObject(args).toString()
        val title = pendingActionTitle(toolName)
        val summary = buildPendingSummary(toolName, args)
        return AiPendingAction(toolName = toolName, argsJson = argsJson, title = title, summary = summary)
    }

    private fun pendingActionTitle(toolName: String): String = when (toolName) {
        AiToolRegistry.CREATE_BOOKING -> "Xác nhận tạo đơn"
        AiToolRegistry.CREATE_DIRECT_BOOKING -> "Xác nhận đặt thợ trực tiếp"
        AiToolRegistry.CANCEL_BOOKING -> "Xác nhận hủy đơn"
        AiToolRegistry.ACCEPT_BID -> "Xác nhận chọn báo giá"
        AiToolRegistry.CONFIRM_COMPLETION -> "Xác nhận hoàn thành đơn"
        AiToolRegistry.REJECT_COMPLETION -> "Xác nhận từ chối hoàn thành"
        AiToolRegistry.SUBMIT_REVIEW -> "Xác nhận gửi đánh giá"
        AiToolRegistry.PLACE_BID -> "Xác nhận gửi báo giá"
        AiToolRegistry.ACCEPT_DIRECT_BOOKING -> "Xác nhận nhận đơn"
        AiToolRegistry.DECLINE_DIRECT_BOOKING -> "Xác nhận từ chối đơn"
        AiToolRegistry.START_JOB -> "Bắt đầu công việc"
        AiToolRegistry.COMPLETE_JOB -> "Báo hoàn thành"
        AiToolRegistry.SET_AVAILABILITY -> "Đổi trạng thái sẵn sàng"
        else -> "Xác nhận"
    }

    private fun buildPendingSummary(toolName: String, args: JsonObject): String = when (toolName) {
        AiToolRegistry.CREATE_BOOKING -> buildString {
            args.s("category")?.let { append("Danh mục: $it\n") }
            args.s("address")?.let { append("Địa chỉ: $it\n") }
            args.s("scheduledAt")?.let { append("Thời gian: $it\n") }
            args.s("description")?.let { append("Mô tả: ${it.take(120)}") }
        }.trim()
        AiToolRegistry.CREATE_DIRECT_BOOKING -> buildString {
            args.s("category")?.let { append("Danh mục: $it\n") }
            args.s("workerId")?.let { append("Thợ: ${it.take(8)}…\n") }
            args.s("address")?.let { append("Địa chỉ: $it\n") }
            args.s("scheduledAt")?.let { append("Thời gian: $it\n") }
            args.s("description")?.let { append("Mô tả: ${it.take(120)}") }
        }.trim()
        AiToolRegistry.CANCEL_BOOKING -> buildString {
            append("Hủy đơn #${args.s("bookingId")?.take(8)?.uppercase() ?: ""}")
            args.s("reason")?.let { append("\nLý do: $it") }
        }
        AiToolRegistry.ACCEPT_BID -> "Chọn báo giá #${args.s("bidId")?.take(8)?.uppercase() ?: ""}"
        AiToolRegistry.CONFIRM_COMPLETION ->
            "Xác nhận đơn #${args.s("bookingId")?.take(8)?.uppercase() ?: ""} đã hoàn thành.\n" +
                "Tiền đang giữ sẽ được giải ngân cho thợ."
        AiToolRegistry.REJECT_COMPLETION -> buildString {
            append("Từ chối hoàn thành đơn #${args.s("bookingId")?.take(8)?.uppercase() ?: ""}")
            args.s("reason")?.let { append("\nLý do: $it") }
        }
        AiToolRegistry.SUBMIT_REVIEW -> buildString {
            append("Đánh giá ${args.d("rating")?.toInt() ?: 5}★")
            args.s("comment")?.let { append("\n\"${it.take(140)}\"") }
        }
        AiToolRegistry.PLACE_BID -> buildString {
            val price = args.d("price") ?: 0.0
            append("Báo giá ${formatCurrencyVnd(price)}")
            args.d("durationHours")?.let { append(" • ${it}h") }
            args.s("message")?.let { append("\n\"${it.take(140)}\"") }
        }
        AiToolRegistry.ACCEPT_DIRECT_BOOKING ->
            "Nhận đơn #${args.s("bookingId")?.take(8)?.uppercase() ?: ""}"
        AiToolRegistry.DECLINE_DIRECT_BOOKING -> buildString {
            append("Từ chối đơn #${args.s("bookingId")?.take(8)?.uppercase() ?: ""}")
            args.s("reason")?.let { append("\nLý do: $it") }
        }
        AiToolRegistry.START_JOB ->
            "Bắt đầu công việc cho đơn #${args.s("bookingId")?.take(8)?.uppercase() ?: ""}"
        AiToolRegistry.COMPLETE_JOB -> buildString {
            append("Báo hoàn thành đơn #${args.s("bookingId")?.take(8)?.uppercase() ?: ""}")
            args.s("note")?.let { append("\nGhi chú: $it") }
        }
        AiToolRegistry.SET_AVAILABILITY ->
            if (args.b("isAvailable") == true) "Bật trạng thái sẵn sàng nhận việc"
            else "Tắt trạng thái sẵn sàng (tạm nghỉ)"
        else -> "Bạn có chắc muốn thực hiện?"
    }

    private fun pendingActionIntroText(toolName: String): String = when (toolName) {
        AiToolRegistry.CREATE_BOOKING -> "Mình đã chuẩn bị đơn dịch vụ. Bạn xác nhận để gửi nhé:"
        AiToolRegistry.CREATE_DIRECT_BOOKING -> "Mình đã chuẩn bị đơn đặt thợ. Bạn xác nhận để gửi nhé:"
        AiToolRegistry.CANCEL_BOOKING -> "Mình đã chuẩn bị yêu cầu hủy đơn. Bạn xác nhận giúp mình nhé:"
        AiToolRegistry.ACCEPT_BID -> "Bạn xác nhận chọn báo giá này nhé?"
        AiToolRegistry.CONFIRM_COMPLETION -> "Bạn xác nhận đơn đã hoàn thành nhé?"
        AiToolRegistry.REJECT_COMPLETION -> "Bạn xác nhận từ chối hoàn thành nhé?"
        AiToolRegistry.SUBMIT_REVIEW -> "Mình đã soạn đánh giá. Bạn xác nhận để gửi nhé:"
        AiToolRegistry.PLACE_BID -> "Mình đã chuẩn bị báo giá. Bạn xác nhận để gửi nhé:"
        AiToolRegistry.ACCEPT_DIRECT_BOOKING -> "Bạn xác nhận nhận đơn này nhé?"
        AiToolRegistry.DECLINE_DIRECT_BOOKING -> "Bạn xác nhận từ chối đơn này nhé?"
        AiToolRegistry.START_JOB -> "Bạn xác nhận bắt đầu công việc nhé?"
        AiToolRegistry.COMPLETE_JOB -> "Bạn xác nhận đã hoàn thành công việc nhé?"
        AiToolRegistry.SET_AVAILABILITY -> "Bạn xác nhận đổi trạng thái nhé?"
        else -> "Vui lòng xác nhận hành động:"
    }

    private fun friendlyActionResult(toolName: String, obj: JsonObject?): String {
        val rawError = obj?.get("error")?.jsonPrimitive?.contentOrNull
        if (rawError != null) {
            return "Rất tiếc, mình chưa thực hiện được: ${sanitizeError(rawError)}"
        }
        val message = obj?.get("message")?.jsonPrimitive?.contentOrNull
        return message ?: when (toolName) {
            AiToolRegistry.CREATE_BOOKING -> "Đã tạo đơn thành công."
            AiToolRegistry.CREATE_DIRECT_BOOKING -> "Đã đặt thợ thành công."
            AiToolRegistry.CANCEL_BOOKING -> "Đã hủy đơn thành công."
            AiToolRegistry.ACCEPT_BID -> "Đã chấp nhận báo giá."
            AiToolRegistry.CONFIRM_COMPLETION -> "Đã xác nhận hoàn thành."
            AiToolRegistry.REJECT_COMPLETION -> "Đã gửi từ chối hoàn thành."
            AiToolRegistry.SUBMIT_REVIEW -> "Đã gửi đánh giá. Cảm ơn bạn!"
            AiToolRegistry.PLACE_BID -> "Đã gửi báo giá."
            AiToolRegistry.ACCEPT_DIRECT_BOOKING -> "Đã nhận đơn."
            AiToolRegistry.DECLINE_DIRECT_BOOKING -> "Đã từ chối đơn."
            AiToolRegistry.START_JOB -> "Đã bắt đầu công việc."
            AiToolRegistry.COMPLETE_JOB -> "Đã báo hoàn thành."
            AiToolRegistry.SET_AVAILABILITY -> "Đã cập nhật trạng thái."
            else -> "Đã hoàn tất."
        }
    }

    private fun sanitizeError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("uuid") -> "mã không hợp lệ"
            lower.contains("permission") || lower.contains("rls") ||
                lower.contains("policy") || lower.contains("401") ||
                lower.contains("403") -> "bạn không có quyền thực hiện thao tác này"
            lower.contains("http") || lower.contains("token") ||
                lower.contains("bearer") || lower.contains("supabase") ||
                lower.contains("url") -> "có lỗi kết nối, vui lòng thử lại"
            raw.length > 80 -> "đã có lỗi xảy ra, vui lòng thử lại"
            else -> raw
        }
    }

    private fun toolDisplayName(name: String): String = when (name) {
        AiToolRegistry.SEARCH_WORKERS -> "tìm thợ"
        AiToolRegistry.GET_WORKER_PROFILE -> "xem hồ sơ thợ"
        AiToolRegistry.GET_WORKER_REVIEWS -> "xem đánh giá thợ"
        AiToolRegistry.GET_MY_BOOKINGS -> "tra danh sách đơn"
        AiToolRegistry.GET_BOOKING_STATUS -> "xem trạng thái đơn"
        AiToolRegistry.GET_BIDS_FOR_BOOKING -> "xem báo giá"
        AiToolRegistry.GET_OPEN_REQUESTS -> "tìm yêu cầu công việc"
        AiToolRegistry.GET_MY_ANALYTICS -> "xem thống kê thu nhập"
        AiToolRegistry.GET_MY_BIDS -> "xem báo giá đã gửi"
        AiToolRegistry.GET_PENDING_DIRECT_BOOKINGS -> "xem đơn chờ nhận"
        AiToolRegistry.GET_MY_WALLET -> "xem ví"
        AiToolRegistry.GET_MY_WALLET_TRANSACTIONS -> "xem lịch sử giao dịch"
        AiToolRegistry.GET_UNREAD_NOTIFICATIONS -> "xem thông báo"
        AiToolRegistry.OPEN_SCREEN -> "chuẩn bị mở màn hình"
        else -> "thực thi $name"
    }

    /**
     * Compact system prompt — the model already sees full tool schemas via
     * the `tools` array, so listing them again here just burns tokens.
     */
    private fun systemPrompt(role: UserRole): String {
        val who = if (role == UserRole.WORKER) "thợ dịch vụ" else "khách hàng"
        return """
            Bạn là trợ lý AI của FixBid, hỗ trợ $who người Việt. Trả lời ngắn gọn,
            tiếng Việt, có thể dùng Markdown (**bold**, danh sách, link).

            Quy tắc:
            - Cần dữ liệu → GỌI tool tương ứng. Không bịa.
            - bookingId / workerId / bidId BẮT BUỘC là UUID lấy từ tool. KHÔNG dùng
              "đơn thứ 2" hay tự bịa. Gọi tool tra cứu trước rồi mới gọi tool action.
            - Yêu cầu phức tạp: lập kế hoạch, gọi nhiều tool liên tiếp trong 1 lượt.
            - Tool action: chỉ cần gọi với args đúng, UI sẽ tự hỏi xác nhận.
            - Tiền VND. Danh sách dùng `-`.
        """.trimIndent()
    }

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    private companion object {
        const val MAX_TOOL_ROUNDS = 4
        const val MAX_HISTORY_TURNS = 6
        const val MAX_TOKENS = 768
        const val MAX_RATE_LIMIT_RETRIES = 2
        const val GENERIC_ERROR = "Mình đang gặp trục trặc kết nối, bạn thử lại sau giây lát nhé."
        const val RATE_LIMIT_ERROR = "Trợ lý đang bận quá tải. Bạn thử lại sau ít giây nhé — " +
            "hoặc bấm nút làm mới để bắt đầu hội thoại mới (giúp giảm số token cần xử lý)."
        val UUID_REGEX = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
