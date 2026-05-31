package com.example.fixbid.data.repository

import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.data.remote.groq.AiToolRegistry
import com.example.fixbid.data.remote.groq.GroqApi
import com.example.fixbid.data.remote.groq.GroqChatRequest
import com.example.fixbid.data.remote.groq.GroqFunctionCall
import com.example.fixbid.data.remote.groq.GroqMessage
import com.example.fixbid.data.remote.groq.GroqToolCall
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.domain.repository.AiAgentRepository
import com.example.fixbid.domain.repository.AiHistoryTurn
import com.example.fixbid.domain.repository.AiPendingAction
import com.example.fixbid.domain.repository.AiReply
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class AiAgentRepositoryImpl @Inject constructor(
    private val groqApi: GroqApi,
    private val toolExecutor: AiToolExecutor
) : AiAgentRepository {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun sendMessage(
        userMessage: String,
        history: List<AiHistoryTurn>,
        role: UserRole
    ): Resource<AiReply> {
        return try {
            Resource.Success(runConversation(userMessage, history, role))
        } catch (e: Exception) {
            Resource.Error(GENERIC_ERROR)
        }
    }

    override suspend fun confirmAction(action: AiPendingAction, role: UserRole): Resource<AiReply> {
        return try {
            val args = runCatching { json.parseToJsonElement(action.argsJson).jsonObject }
                .getOrElse { JsonObject(emptyMap()) }
            val result = toolExecutor.execute(action.toolName, args)
            val msg = friendlyActionResult(action.toolName, result.resultJson)
            Resource.Success(AiReply(text = msg))
        } catch (e: Exception) {
            // Never surface raw exceptions (they can contain URLs / auth tokens).
            Resource.Success(AiReply(text = "Rất tiếc, mình chưa thực hiện được thao tác này. Bạn thử lại sau nhé."))
        }
    }

    private suspend fun runConversation(
        userMessage: String,
        history: List<AiHistoryTurn>,
        role: UserRole
    ): AiReply {
        val messages = mutableListOf<GroqMessage>()
        messages += GroqMessage(role = "system", content = systemPrompt(role))
        history.takeLast(10).forEach { turn ->
            messages += GroqMessage(
                role = if (turn.isUser) "user" else "assistant",
                content = turn.text
            )
        }
        messages += GroqMessage(role = "user", content = userMessage)

        val tools = AiToolRegistry.toolsFor(role)
        var pendingNavigation: String? = null

        repeat(MAX_TOOL_ROUNDS) {
            val response = groqApi.chat(
                GroqChatRequest(
                    model = GroqApi.DEFAULT_MODEL,
                    messages = messages,
                    tools = tools,
                    toolChoice = "auto"
                )
            )
            val choice = response.choices.firstOrNull()
                ?: return AiReply("Xin lỗi, tôi chưa thể trả lời ngay lúc này.")
            val msg = choice.message
            val toolCalls = msg.toolCalls

            if (toolCalls.isNullOrEmpty()) {
                return AiReply(
                    text = msg.content?.trim().orEmpty()
                        .ifBlank { "Mình chưa rõ yêu cầu, bạn nói rõ hơn giúp mình nhé." },
                    navigationRoute = pendingNavigation
                )
            }

            // If the model wants an ACTION tool, validate its args first. If a
            // required id isn't a real UUID (e.g. the model passed "đơn thứ 2"),
            // feed an error back so it self-corrects by looking up the real id,
            // instead of executing with garbage. Only confirm when args are valid.
            val actionCall = toolCalls.firstOrNull { AiToolRegistry.isActionTool(it.function.name) }
            if (actionCall != null) {
                val args = parseArgs(actionCall.function)
                val validationError = validateActionArgs(actionCall.function.name, args)
                if (validationError == null) {
                    return AiReply(
                        text = pendingActionIntroText(actionCall.function.name),
                        navigationRoute = pendingNavigation,
                        pendingAction = buildPendingAction(actionCall.function.name, args)
                    )
                }
                // Invalid → echo tool_calls then return an error result so the model
                // can retry (typically by calling get_my_bookings / get_open_requests).
                messages += GroqMessage(role = "assistant", content = msg.content, toolCalls = toolCalls)
                for (call in toolCalls) {
                    val resultJson = if (call.id == actionCall.id) {
                        """{"error":"$validationError"}"""
                    } else {
                        runToolCall(call).resultJson
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

            // Otherwise run read tools and feed results back.
            messages += GroqMessage(role = "assistant", content = msg.content, toolCalls = toolCalls)
            for (call in toolCalls) {
                val result = runToolCall(call)
                if (result.navigationRoute != null) pendingNavigation = result.navigationRoute
                messages += GroqMessage(
                    role = "tool",
                    toolCallId = call.id,
                    name = call.function.name,
                    content = result.resultJson
                )
            }
        }

        val finalResp = groqApi.chat(
            GroqChatRequest(model = GroqApi.DEFAULT_MODEL, messages = messages)
        )
        return AiReply(
            text = finalResp.choices.firstOrNull()?.message?.content?.trim()
                ?: "Mình đã xử lý xong yêu cầu của bạn.",
            navigationRoute = pendingNavigation
        )
    }

    private suspend fun runToolCall(call: GroqToolCall): ToolRunResult {
        val args = parseArgs(call.function)
        return runCatching { toolExecutor.execute(call.function.name, args) }
            .getOrElse { ToolRunResult("""{"error":"Lỗi thực thi công cụ"}""") }
    }

    private fun parseArgs(fn: GroqFunctionCall): JsonObject =
        runCatching { json.parseToJsonElement(fn.arguments).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }

    private fun JsonObject.s(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.d(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    /** Returns an error string if an action tool's args are invalid, else null. */
    private fun validateActionArgs(toolName: String, args: JsonObject): String? {
        val needsBookingId = toolName == AiToolRegistry.CANCEL_BOOKING ||
            toolName == AiToolRegistry.SUBMIT_REVIEW ||
            toolName == AiToolRegistry.PLACE_BID
        if (needsBookingId) {
            val id = args.s("bookingId")
                ?: return "Thiếu mã đơn (bookingId). Hãy gọi công cụ tra cứu danh sách để lấy mã đơn UUID thật, rồi dùng đúng giá trị 'bookingId' đó."
            if (!UUID_REGEX.matches(id)) {
                return "bookingId '$id' không phải mã UUID hợp lệ. KHÔNG được tự bịa hoặc dùng cụm từ như 'đơn thứ 2'. Hãy gọi get_my_bookings (hoặc get_open_requests) để lấy đúng trường 'bookingId' của đơn, rồi gọi lại."
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
        return null
    }

    private fun buildPendingAction(toolName: String, args: JsonObject): AiPendingAction {
        val argsJson = JsonObject(args).toString()
        return when (toolName) {
            AiToolRegistry.CANCEL_BOOKING -> AiPendingAction(
                toolName = toolName,
                argsJson = argsJson,
                title = "Xác nhận hủy đơn",
                summary = buildString {
                    append("Hủy đơn #${args.s("bookingId")?.take(8)?.uppercase() ?: ""}")
                    args.s("reason")?.let { append("\nLý do: $it") }
                }
            )
            AiToolRegistry.SUBMIT_REVIEW -> AiPendingAction(
                toolName = toolName,
                argsJson = argsJson,
                title = "Xác nhận gửi đánh giá",
                summary = buildString {
                    append("Đánh giá ${args.d("rating")?.toInt() ?: 5}★")
                    args.s("comment")?.let { append("\n\"$it\"") }
                }
            )
            AiToolRegistry.PLACE_BID -> AiPendingAction(
                toolName = toolName,
                argsJson = argsJson,
                title = "Xác nhận gửi báo giá",
                summary = buildString {
                    val price = args.d("price") ?: 0.0
                    append("Báo giá ${formatCurrencyVnd(price)}")
                    args.d("durationHours")?.let { append(" • ${it}h") }
                    args.s("message")?.let { append("\n\"$it\"") }
                }
            )
            else -> AiPendingAction(toolName, argsJson, "Xác nhận", "Bạn có chắc muốn thực hiện?")
        }
    }

    private fun pendingActionIntroText(toolName: String): String = when (toolName) {
        AiToolRegistry.CANCEL_BOOKING -> "Mình đã chuẩn bị yêu cầu hủy đơn. Bạn xác nhận giúp mình nhé:"
        AiToolRegistry.SUBMIT_REVIEW -> "Mình đã soạn đánh giá. Bạn xác nhận để gửi nhé:"
        AiToolRegistry.PLACE_BID -> "Mình đã chuẩn bị báo giá. Bạn xác nhận để gửi nhé:"
        else -> "Vui lòng xác nhận hành động:"
    }

    private fun friendlyActionResult(toolName: String, resultJson: String): String {
        val obj = runCatching { json.parseToJsonElement(resultJson).jsonObject }.getOrNull()
        val rawError = obj?.get("error")?.jsonPrimitive?.contentOrNull
        if (rawError != null) {
            // Sanitize: never echo URLs/tokens/SQL internals back to the user.
            return "Rất tiếc, mình chưa thực hiện được: ${sanitizeError(rawError)}"
        }
        val message = obj?.get("message")?.jsonPrimitive?.contentOrNull
        return message ?: when (toolName) {
            AiToolRegistry.CANCEL_BOOKING -> "Đã hủy đơn thành công."
            AiToolRegistry.SUBMIT_REVIEW -> "Đã gửi đánh giá. Cảm ơn bạn!"
            AiToolRegistry.PLACE_BID -> "Đã gửi báo giá."
            else -> "Đã hoàn tất."
        }
    }

    /** Map low-level/leaky errors to safe, user-friendly Vietnamese messages. */
    private fun sanitizeError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("uuid") -> "mã đơn không hợp lệ"
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

    private fun systemPrompt(role: UserRole): String {
        val who = if (role == UserRole.WORKER) "thợ dịch vụ" else "khách hàng"
        val roleTools = if (role == UserRole.WORKER) {
            "- Thợ: xem yêu cầu mở (get_open_requests), xem thống kê thu nhập (get_my_analytics), " +
                "đặt báo giá (place_bid - cần xác nhận)."
        } else {
            "- Khách: tìm thợ (search_workers), xem đơn (get_my_bookings), hủy đơn (cancel_booking - " +
                "cần xác nhận), gửi đánh giá (submit_review - cần xác nhận)."
        }
        return """
            Bạn là trợ lý AI của FixBid - nền tảng kết nối khách hàng với thợ sửa chữa, vệ sinh,
            điện nước... tại Việt Nam. Bạn đang hỗ trợ một $who.

            Nguyên tắc:
            - Trả lời ngắn gọn, thân thiện, bằng tiếng Việt.
            - Khi cần dữ liệu (tìm thợ, xem đơn, trạng thái, thống kê...) hãy GỌI CÔNG CỤ phù hợp,
              không bịa thông tin. Chỉ trả lời dựa trên dữ liệu công cụ trả về.
            - QUAN TRỌNG: với hành động thay đổi dữ liệu (hủy đơn, gửi đánh giá, đặt báo giá), tham số
              'bookingId' BẮT BUỘC phải là mã UUID THẬT lấy từ kết quả công cụ (trường "bookingId").
              TUYỆT ĐỐI KHÔNG được dùng cụm từ mô tả như "đơn thứ 2", "đơn mới nhất", hay tự bịa mã.
              Nếu người dùng nói chung chung, hãy GỌI get_my_bookings (hoặc get_open_requests) TRƯỚC
              để lấy đúng 'bookingId', rồi mới gọi công cụ hành động với mã đó.
            - Sau khi gọi công cụ hành động, hệ thống sẽ hỏi người dùng xác nhận trước khi thực thi.
            - Không bịa mã đơn, giá, tên thợ. Nếu không có dữ liệu, nói rõ là chưa có.
            - Định dạng tiền theo VND. Trình bày danh sách gọn gàng, dễ đọc.
            Công cụ khả dụng:
            $roleTools
        """.trimIndent()
    }

    private companion object {
        const val MAX_TOOL_ROUNDS = 3
        const val GENERIC_ERROR = "Mình đang gặp trục trặc kết nối, bạn thử lại sau giây lát nhé."
        val UUID_REGEX = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
