package com.example.fixbid.data.remote.groq

import com.example.fixbid.domain.model.UserRole
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Declares the tools (functions) the AI assistant may call, scoped by role.
 * Each tool maps to an existing use case executed by [com.example.fixbid.data.repository.AiToolExecutor].
 *
 * Read tools run immediately during the model's tool loop. Action tools
 * (create/cancel/review/bid) are NOT auto-executed — they require explicit user
 * confirmation in the UI before the underlying use case runs.
 */
object AiToolRegistry {

    // ── Read / navigation tools ──────────────────────────────────────────────
    const val SEARCH_WORKERS = "search_workers"
    const val GET_MY_BOOKINGS = "get_my_bookings"
    const val GET_BOOKING_STATUS = "get_booking_status"
    const val OPEN_SCREEN = "open_screen"
    const val GET_OPEN_REQUESTS = "get_open_requests"     // worker
    const val GET_MY_ANALYTICS = "get_my_analytics"       // worker

    // ── Action tools (require confirmation) ──────────────────────────────────
    const val CANCEL_BOOKING = "cancel_booking"           // customer
    const val SUBMIT_REVIEW = "submit_review"             // customer
    const val PLACE_BID = "place_bid"                     // worker

    /** Action tools must be confirmed by the user before executing. */
    fun isActionTool(name: String): Boolean = name in setOf(
        CANCEL_BOOKING, SUBMIT_REVIEW, PLACE_BID
    )

    fun toolsFor(role: UserRole): List<GroqTool> = when (role) {
        UserRole.WORKER -> buildList {
            add(getOpenRequests())
            add(getMyAnalytics())
            add(getBookingStatus())
            add(placeBid())
            add(openScreen())
        }
        UserRole.CUSTOMER -> buildList {
            add(searchWorkers())
            add(getMyBookings())
            add(getBookingStatus())
            add(cancelBooking())
            add(submitReview())
            add(openScreen())
        }
    }

    private fun stringProp(description: String, enum: List<String>? = null): JsonObject =
        buildJsonObject {
            put("type", "string")
            put("description", description)
            if (enum != null) putJsonArray("enum") { enum.forEach { add(it) } }
        }

    private fun numberProp(description: String): JsonObject = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    // ── Read tools ────────────────────────────────────────────────────────────

    private fun searchWorkers() = GroqTool(
        function = GroqFunctionDef(
            name = SEARCH_WORKERS,
            description = "Tìm danh sách thợ dịch vụ đang sẵn sàng, có thể lọc theo danh mục, " +
                "giá tối đa mỗi giờ và đánh giá tối thiểu. Dùng khi khách muốn tìm/gợi ý thợ.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("category", stringProp(
                        "Danh mục dịch vụ",
                        enum = CATEGORIES
                    ))
                    put("maxPrice", numberProp("Giá tối đa mỗi giờ (VND)"))
                    put("minRating", numberProp("Đánh giá tối thiểu từ 0 đến 5"))
                }
                putJsonArray("required") {}
            }
        )
    )

    private fun getMyBookings() = GroqTool(
        function = GroqFunctionDef(
            name = GET_MY_BOOKINGS,
            description = "Lấy danh sách đơn đặt dịch vụ của người dùng hiện tại, có thể lọc theo " +
                "trạng thái. Dùng khi khách hỏi 'đơn của tôi', 'lịch sử đặt', trạng thái đơn...",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("status", stringProp(
                        "Trạng thái đơn cần lọc (bỏ trống để lấy tất cả)",
                        enum = STATUSES
                    ))
                }
                putJsonArray("required") {}
            }
        )
    )

    private fun getBookingStatus() = GroqTool(
        function = GroqFunctionDef(
            name = GET_BOOKING_STATUS,
            description = "Xem chi tiết và trạng thái của một đơn cụ thể theo mã đơn (bookingId).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã đơn cần tra cứu"))
                }
                putJsonArray("required") { add("bookingId") }
            }
        )
    )

    private fun getOpenRequests() = GroqTool(
        function = GroqFunctionDef(
            name = GET_OPEN_REQUESTS,
            description = "Lấy danh sách yêu cầu công việc đang mở mà thợ có thể đặt giá (đấu thầu), " +
                "đã lọc theo kỹ năng của thợ. Dùng khi thợ hỏi 'có việc gì mới', 'yêu cầu mở'...",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
                putJsonArray("required") {}
            }
        )
    )

    private fun getMyAnalytics() = GroqTool(
        function = GroqFunctionDef(
            name = GET_MY_ANALYTICS,
            description = "Lấy thống kê thu nhập và hiệu suất của thợ hiện tại (thu nhập tháng này, " +
                "tổng thu nhập, số việc hoàn thành, đánh giá trung bình).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
                putJsonArray("required") {}
            }
        )
    )

    // ── Action tools ────────────────────────────────────────────────────────

    private fun cancelBooking() = GroqTool(
        function = GroqFunctionDef(
            name = CANCEL_BOOKING,
            description = "Hủy một đơn đặt dịch vụ của khách (chỉ khi đơn chưa hoàn thành). " +
                "LUÔN cần người dùng xác nhận trước khi thực thi. Hãy lấy bookingId chính xác " +
                "từ danh sách đơn nếu chưa rõ.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã đơn cần hủy"))
                    put("reason", stringProp("Lý do hủy (tùy chọn)"))
                }
                putJsonArray("required") { add("bookingId") }
            }
        )
    )

    private fun submitReview() = GroqTool(
        function = GroqFunctionDef(
            name = SUBMIT_REVIEW,
            description = "Gửi đánh giá cho thợ sau khi đơn đã hoàn thành. LUÔN cần người dùng " +
                "xác nhận trước khi gửi.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã đơn đã hoàn thành"))
                    put("rating", numberProp("Số sao từ 1 đến 5"))
                    put("comment", stringProp("Nội dung nhận xét (tùy chọn)"))
                }
                putJsonArray("required") { add("bookingId"); add("rating") }
            }
        )
    )

    private fun placeBid() = GroqTool(
        function = GroqFunctionDef(
            name = PLACE_BID,
            description = "Đặt báo giá cho một yêu cầu công việc đang mở. LUÔN cần thợ xác nhận " +
                "trước khi gửi báo giá.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã yêu cầu công việc"))
                    put("price", numberProp("Giá đề xuất (VND)"))
                    put("durationHours", numberProp("Thời gian dự kiến (giờ)"))
                    put("message", stringProp("Lời giới thiệu / giải thích báo giá"))
                }
                putJsonArray("required") { add("bookingId"); add("price"); add("durationHours"); add("message") }
            }
        )
    )

    private fun openScreen() = GroqTool(
        function = GroqFunctionDef(
            name = OPEN_SCREEN,
            description = "Mở một màn hình trong ứng dụng để dẫn người dùng tới đúng nơi. " +
                "Route hợp lệ: 'discover_workers' (tìm thợ), 'notification_list' (thông báo), " +
                "'home' (trang chủ), 'customer_booking_detail/{bookingId}' (chi tiết đơn), " +
                "'worker_public_profile/{workerId}' (hồ sơ thợ).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("route", stringProp("Route màn hình cần mở"))
                }
                putJsonArray("required") { add("route") }
            }
        )
    )

    private val CATEGORIES = listOf(
        "PLUMBING", "ELECTRICAL", "CARPENTRY", "AIR_CONDITIONING",
        "APPLIANCE_REPAIR", "CLEANING", "LOCKSMITH", "ROOFING", "OTHER"
    )
    private val STATUSES = listOf(
        "PENDING", "BIDDING", "AWAITING_PAYMENT", "CONFIRMED",
        "IN_PROGRESS", "PENDING_COMPLETION", "COMPLETED", "CANCELLED"
    )
}
