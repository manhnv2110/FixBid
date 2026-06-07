package com.example.fixbid.domain.usecase.shared

import com.example.fixbid.domain.model.AiContext
import com.example.fixbid.domain.model.AiContextScreen
import com.example.fixbid.domain.model.AiSuggestion
import com.example.fixbid.domain.model.AiSuggestionIcon
import com.example.fixbid.domain.model.AiSuggestionKind
import com.example.fixbid.domain.model.UserRole
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule-based generator that turns an [AiContext] into 2–4 [AiSuggestion]s the
 * screen can render as chips/cards. Pure function (no IO) so it's safe to call
 * from a `remember(context) { … }` block — recomposing on context change.
 *
 * The engine prefers high-signal, action-oriented prompts. Each prompt embeds
 * the relevant UUID so the agent can call its tools without an extra lookup
 * round-trip — saving tokens and latency.
 */
@Singleton
class AiSuggestionEngine @Inject constructor() {

    operator fun invoke(context: AiContext): List<AiSuggestion> = when (context.screen) {
        AiContextScreen.CUSTOMER_BOOKING_DETAIL -> customerBookingDetail(context)
        AiContextScreen.CUSTOMER_BOOKING_HISTORY -> customerBookingHistory(context)
        AiContextScreen.CUSTOMER_WORKER_PROFILE -> customerWorkerProfile(context)
        AiContextScreen.WORKER_JOB_DETAIL -> workerJobDetail(context)
        AiContextScreen.WORKER_HOME -> workerHome(context)
        AiContextScreen.WORKER_MY_BIDS -> workerMyBids(context)
    }

    // ── Customer ── ────────────────────────────────────────────────────────

    private fun customerBookingDetail(c: AiContext): List<AiSuggestion> {
        val bookingId = c.data["bookingId"] as? String ?: return emptyList()
        val status = (c.data["bookingStatus"] as? String).orEmpty().uppercase()
        val category = c.data["category"] as? String
        val quoted = c.data["quotedPrice"] as? Double
        val out = mutableListOf<AiSuggestion>()

        when (status) {
            // Customer just received a quote — this is the highest-value spot.
            "QUOTED" -> {
                out += AiSuggestion(
                    id = "analyze-quote",
                    label = "Đánh giá báo giá",
                    helper = "Giá có hợp lý so với thị trường?",
                    kind = AiSuggestionKind.INLINE_ANALYZE,
                    iconKey = AiSuggestionIcon.Insights,
                    prompt = buildString {
                        append("Giúp tôi đánh giá báo giá cho đơn $bookingId. ")
                        append("Gọi get_booking_status để xem chi tiết. ")
                        if (quoted != null && category != null) {
                            append("Thợ báo giá ${quoted.toLong()}đ cho dịch vụ $category. ")
                        }
                        append("Hãy so sánh với mức giá thị trường, nêu rõ giá có hợp lý không, ")
                        append("và đưa ra 2-3 điểm khách nên lưu ý trước khi chấp nhận. Trả lời ngắn gọn dưới 6 câu.")
                    }
                )
                out += AiSuggestion(
                    id = "draft-reject-reason",
                    label = "Soạn lý do từ chối",
                    helper = "Cần thợ báo lại với mức khác",
                    kind = AiSuggestionKind.PREFILL_CHAT,
                    iconKey = AiSuggestionIcon.Edit,
                    prompt = "Soạn cho tôi 3 mẫu lý do lịch sự để từ chối báo giá hiện tại " +
                        "và đề nghị thợ điều chỉnh. Đơn $bookingId."
                )
            }

            "AWAITING_PAYMENT" -> {
                out += AiSuggestion(
                    id = "explain-fee-breakdown",
                    label = "Số tiền này gồm những gì?",
                    kind = AiSuggestionKind.PREFILL_CHAT,
                    iconKey = AiSuggestionIcon.Question,
                    prompt = "Giải thích tổng tiền tôi sắp thanh toán cho đơn $bookingId " +
                        "gồm phí dịch vụ và phí nền tảng nếu có. Trả lời ngắn."
                )
                out += AiSuggestion(
                    id = "preflight-checks",
                    label = "Kiểm tra trước khi thanh toán",
                    kind = AiSuggestionKind.INLINE_ANALYZE,
                    iconKey = AiSuggestionIcon.Check,
                    prompt = "Trước khi tôi thanh toán đơn $bookingId, hãy gọi get_booking_status " +
                        "rồi liệt kê 3-4 điểm tôi nên đối chiếu (giá, lịch hẹn, địa chỉ, " +
                        "tình trạng thợ). Ngắn, gạch đầu dòng."
                )
            }

            "PENDING_COMPLETION" -> {
                out += AiSuggestion(
                    id = "completion-checklist",
                    label = "Cần kiểm tra gì trước khi xác nhận?",
                    kind = AiSuggestionKind.INLINE_ANALYZE,
                    iconKey = AiSuggestionIcon.Check,
                    prompt = "Thợ báo hoàn thành đơn $bookingId. Gọi get_booking_status để biết chi tiết. " +
                        "Sau đó liệt kê 4 mục khách nên kiểm tra trực tiếp tại hiện trường " +
                        "trước khi xác nhận hoàn thành. Gạch đầu dòng, ngắn."
                )
                out += AiSuggestion(
                    id = "draft-rework-reason",
                    label = "Soạn yêu cầu làm lại",
                    helper = "Nếu chưa hài lòng",
                    kind = AiSuggestionKind.PREFILL_CHAT,
                    iconKey = AiSuggestionIcon.Edit,
                    prompt = "Soạn 2 mẫu lời nhắn lịch sự yêu cầu thợ làm lại đơn $bookingId, " +
                        "nêu rõ điểm chưa đạt và mong muốn của tôi."
                )
            }

            "COMPLETED" -> {
                out += AiSuggestion(
                    id = "draft-review",
                    label = "Soạn đánh giá",
                    helper = "Gợi ý 5★ / 4★ / 3★",
                    kind = AiSuggestionKind.PREFILL_CHAT,
                    iconKey = AiSuggestionIcon.Edit,
                    prompt = "Soạn 3 mẫu đánh giá ngắn (5★, 4★, 3★) cho thợ vừa hoàn thành đơn " +
                        "$bookingId. Mỗi mẫu 1-2 câu, tự nhiên, có thể chỉnh."
                )
            }

            "BIDDING" -> {
                out += AiSuggestion(
                    id = "summarize-bids",
                    label = "Tóm tắt các báo giá",
                    kind = AiSuggestionKind.INLINE_ANALYZE,
                    iconKey = AiSuggestionIcon.Insights,
                    prompt = "Gọi get_bids_for_booking với bookingId $bookingId, " +
                        "tóm tắt số báo giá đã nhận, mức thấp/cao/trung bình, " +
                        "và gợi ý lựa chọn tốt nhất. Trả lời ngắn."
                )
            }

            "PENDING", "CONFIRMED", "IN_PROGRESS" -> {
                out += AiSuggestion(
                    id = "what-next",
                    label = "Tiếp theo tôi cần làm gì?",
                    kind = AiSuggestionKind.PREFILL_CHAT,
                    iconKey = AiSuggestionIcon.Question,
                    prompt = "Đơn $bookingId của tôi đang ở trạng thái $status. " +
                        "Tóm tắt bước tiếp theo tôi nên làm và những gì cần chuẩn bị."
                )
            }
        }

        // Always-on: ask anything about this booking via chat.
        out += AiSuggestion(
            id = "open-chat-context",
            label = "Hỏi thêm về đơn này",
            kind = AiSuggestionKind.PREFILL_CHAT,
            iconKey = AiSuggestionIcon.Sparkle,
            prompt = "Tôi muốn hỏi về đơn $bookingId. "
        )
        return out
    }

    private fun customerBookingHistory(c: AiContext): List<AiSuggestion> {
        val activeCount = (c.data["activeCount"] as? Int) ?: 0
        val out = mutableListOf<AiSuggestion>()
        if (activeCount > 0) {
            out += AiSuggestion(
                id = "history-priority",
                label = "Đơn nào cần xử lý trước?",
                kind = AiSuggestionKind.INLINE_ANALYZE,
                iconKey = AiSuggestionIcon.Insights,
                prompt = "Gọi get_my_bookings rồi xếp hạng các đơn đang xử lý theo độ ưu tiên " +
                    "(đơn cần thanh toán, đơn cần xác nhận hoàn thành, đơn sắp tới giờ hẹn). " +
                    "Liệt kê tối đa 3 đơn, gạch đầu dòng, kèm hành động đề xuất."
            )
        }
        out += AiSuggestion(
            id = "history-summary",
            label = "Tóm tắt lịch sử dịch vụ",
            kind = AiSuggestionKind.INLINE_ANALYZE,
            iconKey = AiSuggestionIcon.Insights,
            prompt = "Gọi get_my_bookings, tóm tắt số đơn đã hoàn thành, đã chi tiêu, " +
                "và 1-2 thợ tôi hay dùng nhất. Ngắn gọn 4-5 dòng."
        )
        return out
    }

    private fun customerWorkerProfile(c: AiContext): List<AiSuggestion> {
        val workerId = c.data["workerId"] as? String ?: return emptyList()
        val workerName = c.data["workerName"] as? String
        return listOf(
            AiSuggestion(
                id = "worker-trust-check",
                label = "Thợ này có đáng tin?",
                kind = AiSuggestionKind.INLINE_ANALYZE,
                iconKey = AiSuggestionIcon.Insights,
                prompt = "Gọi get_worker_profile và get_worker_reviews cho workerId $workerId. " +
                    "Đánh giá ngắn gọn về độ tin cậy dựa trên rating, số đơn đã làm, " +
                    "và 2-3 điểm đáng chú ý từ review gần đây. Trả lời 5-6 dòng."
            ),
            AiSuggestion(
                id = "worker-fair-price",
                label = "Giá có hợp lý không?",
                kind = AiSuggestionKind.PREFILL_CHAT,
                iconKey = AiSuggestionIcon.Compare,
                prompt = "So sánh giá theo giờ của ${workerName ?: "thợ này"} (workerId $workerId) " +
                    "với mặt bằng chung của cùng lĩnh vực. Cho biết có nên chọn không."
            ),
            AiSuggestion(
                id = "draft-direct-booking",
                label = "Soạn yêu cầu đặt thợ",
                kind = AiSuggestionKind.PREFILL_CHAT,
                iconKey = AiSuggestionIcon.Edit,
                prompt = "Tôi muốn đặt trực tiếp ${workerName ?: "thợ"} (workerId $workerId). " +
                    "Hỏi tôi vài câu cần thiết để tạo đơn (mô tả công việc, địa chỉ, thời gian)."
            )
        )
    }

    // ── Worker ── ──────────────────────────────────────────────────────────

    private fun workerJobDetail(c: AiContext): List<AiSuggestion> {
        val bookingId = c.data["bookingId"] as? String ?: return emptyList()
        val status = (c.data["bookingStatus"] as? String).orEmpty().uppercase()
        val type = (c.data["bookingType"] as? String).orEmpty().uppercase()
        val competitorCount = (c.data["competitorBidsCount"] as? Int) ?: 0
        val averageBid = c.data["averageBid"] as? Double
        val out = mutableListOf<AiSuggestion>()

        when {
            // Direct booking awaiting our quote → this is the prime spot.
            status == "PENDING" && type == "DIRECT" -> {
                out += AiSuggestion(
                    id = "suggest-quote-price",
                    label = "Đề xuất mức báo giá",
                    helper = "Dựa vào yêu cầu và mặt bằng",
                    kind = AiSuggestionKind.INLINE_ANALYZE,
                    iconKey = AiSuggestionIcon.Insights,
                    prompt = "Gọi get_booking_status với bookingId $bookingId. " +
                        "Đề xuất một mức giá hợp lý kèm khoảng (thấp - khuyến nghị - cao), " +
                        "lý giải ngắn gọn 1-2 câu vì sao. Tiền VND."
                )
                out += AiSuggestion(
                    id = "draft-quote-message",
                    label = "Soạn lời nhắn báo giá",
                    kind = AiSuggestionKind.PREFILL_CHAT,
                    iconKey = AiSuggestionIcon.Edit,
                    prompt = "Soạn 2 mẫu lời nhắn ngắn (≤ 80 từ) thuyết phục khách chấp nhận báo giá " +
                        "cho đơn $bookingId. Tự tin nhưng không khoa trương."
                )
            }

            status == "QUOTED" -> {
                out += AiSuggestion(
                    id = "improve-quote",
                    label = "Cách tăng cơ hội được chọn?",
                    kind = AiSuggestionKind.PREFILL_CHAT,
                    iconKey = AiSuggestionIcon.Question,
                    prompt = "Tôi đã báo giá đơn $bookingId. Gợi ý 3-4 cách tăng cơ hội " +
                        "khách chấp nhận (giá, lời nhắn, thời gian). Ngắn gọn."
                )
            }

            status == "BIDDING" -> {
                out += AiSuggestion(
                    id = "competitive-bid",
                    label = "Mức giá nào cạnh tranh?",
                    kind = AiSuggestionKind.INLINE_ANALYZE,
                    iconKey = AiSuggestionIcon.Compare,
                    prompt = buildString {
                        append("Có $competitorCount thợ khác đã báo giá đơn $bookingId. ")
                        if (averageBid != null) append("Trung bình ${averageBid.toLong()}đ. ")
                        append("Đề xuất mức tôi nên đặt để vừa cạnh tranh vừa có lãi, ")
                        append("kèm 1 mẹo viết lời giới thiệu. Trả lời 4-5 dòng.")
                    }
                )
            }

            status == "CONFIRMED" -> {
                out += AiSuggestion(
                    id = "preflight-job",
                    label = "Cần chuẩn bị gì cho đơn này?",
                    kind = AiSuggestionKind.INLINE_ANALYZE,
                    iconKey = AiSuggestionIcon.Check,
                    prompt = "Gọi get_booking_status cho đơn $bookingId. " +
                        "Liệt kê 4-5 thứ thợ nên chuẩn bị (dụng cụ, đường đi, vật tư) " +
                        "trước khi đến. Gạch đầu dòng."
                )
            }

            status == "IN_PROGRESS" -> {
                out += AiSuggestion(
                    id = "completion-tips",
                    label = "Mẹo báo hoàn thành chuẩn",
                    kind = AiSuggestionKind.PREFILL_CHAT,
                    iconKey = AiSuggestionIcon.Question,
                    prompt = "Tôi đang làm đơn $bookingId. Gợi ý cách báo hoàn thành để khách " +
                        "xác nhận nhanh và đánh giá tốt: ảnh chụp, ghi chú gì, lưu ý gì. Ngắn gọn."
                )
            }
        }

        out += AiSuggestion(
            id = "open-chat-job",
            label = "Hỏi về đơn này",
            kind = AiSuggestionKind.PREFILL_CHAT,
            iconKey = AiSuggestionIcon.Sparkle,
            prompt = "Tôi muốn hỏi về đơn $bookingId. "
        )
        return out
    }

    private fun workerHome(c: AiContext): List<AiSuggestion> {
        val pendingDirect = (c.data["pendingDirectCount"] as? Int) ?: 0
        val openRequests = (c.data["openRequestCount"] as? Int) ?: 0
        val out = mutableListOf<AiSuggestion>()
        if (pendingDirect > 0) {
            out += AiSuggestion(
                id = "summarize-direct",
                label = "Đơn trực tiếp đáng nhận?",
                kind = AiSuggestionKind.INLINE_ANALYZE,
                iconKey = AiSuggestionIcon.Insights,
                prompt = "Gọi get_pending_direct_bookings, đánh giá nhanh từng đơn (giá tiềm năng, " +
                    "khoảng cách, thời gian) và đề xuất nên báo giá đơn nào trước. Tối đa 3 đơn."
            )
        }
        if (openRequests > 0) {
            out += AiSuggestion(
                id = "summarize-open",
                label = "Yêu cầu mở phù hợp với tôi",
                kind = AiSuggestionKind.INLINE_ANALYZE,
                iconKey = AiSuggestionIcon.Compare,
                prompt = "Gọi get_open_requests (applySkillsFilter=true), tóm tắt 3 yêu cầu " +
                    "phù hợp nhất với kỹ năng của tôi và gợi ý mức giá nên đặt. Ngắn gọn."
            )
        }
        out += AiSuggestion(
            id = "earnings-insight",
            label = "Phân tích thu nhập tháng này",
            kind = AiSuggestionKind.INLINE_ANALYZE,
            iconKey = AiSuggestionIcon.Insights,
            prompt = "Gọi get_my_analytics, so sánh thu nhập tháng này với tháng trước, " +
                "chỉ ra xu hướng và 1-2 hành động giúp tăng thu nhập. Ngắn 5-6 dòng."
        )
        return out
    }

    private fun workerMyBids(c: AiContext): List<AiSuggestion> {
        return listOf(
            AiSuggestion(
                id = "bids-effectiveness",
                label = "Báo giá nào hiệu quả?",
                kind = AiSuggestionKind.INLINE_ANALYZE,
                iconKey = AiSuggestionIcon.Insights,
                prompt = "Gọi get_my_bids, phân tích tỉ lệ thắng theo mức giá / lĩnh vực " +
                    "và đề xuất 2 cách cải thiện tỉ lệ trúng thầu. 5-6 dòng."
            )
        )
    }

    /**
     * Helper for screens that don't yet pass full data — returns a single
     * generic chip so the UI surface still renders something useful.
     */
    fun fallback(role: UserRole): List<AiSuggestion> = listOf(
        AiSuggestion(
            id = "open-chat",
            label = "Hỏi trợ lý AI",
            kind = AiSuggestionKind.PREFILL_CHAT,
            iconKey = AiSuggestionIcon.Sparkle,
            prompt = if (role == UserRole.WORKER)
                "Mình có thể giúp gì cho công việc của bạn?"
            else
                "Mình có thể giúp gì cho bạn?"
        )
    )
}
