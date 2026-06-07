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
 *
 * Tools are grouped into:
 *   - **Read tools**: run immediately during the model's tool loop. They return
 *     compact JSON the model can quote to the user.
 *   - **Action tools**: NOT auto-executed. The agent loop intercepts the call,
 *     validates arguments, and surfaces an [com.example.fixbid.domain.repository.AiPendingAction]
 *     so the UI can ask for explicit confirmation before running.
 *
 * Adding a new tool is a 4-step contract:
 *   1. Add a name constant here.
 *   2. Build its [GroqTool] schema and include it in the appropriate
 *      [toolsFor] role list.
 *   3. Implement execution in [com.example.fixbid.data.repository.AiToolExecutor].
 *   4. If destructive: add it to [isActionTool] AND add validation + pending
 *      action UX in [com.example.fixbid.data.repository.AiAgentRepositoryImpl].
 */
object AiToolRegistry {

    // ── Read / navigation tools ──────────────────────────────────────────────
    const val SEARCH_WORKERS = "search_workers"
    const val GET_WORKER_PROFILE = "get_worker_profile"
    const val GET_WORKER_REVIEWS = "get_worker_reviews"
    const val GET_MY_BOOKINGS = "get_my_bookings"
    const val GET_BOOKING_STATUS = "get_booking_status"
    const val GET_BIDS_FOR_BOOKING = "get_bids_for_booking"
    const val GET_MY_WALLET = "get_my_wallet"
    const val GET_MY_WALLET_TRANSACTIONS = "get_my_wallet_transactions"
    const val GET_UNREAD_NOTIFICATIONS = "get_unread_notifications"
    const val OPEN_SCREEN = "open_screen"

    // Worker reads
    const val GET_OPEN_REQUESTS = "get_open_requests"
    const val GET_MY_ANALYTICS = "get_my_analytics"
    const val GET_MY_BIDS = "get_my_bids"
    const val GET_PENDING_DIRECT_BOOKINGS = "get_pending_direct_bookings"

    // ── Action tools (require confirmation) ──────────────────────────────────
    // Customer
    const val CREATE_BOOKING = "create_booking"
    const val CREATE_DIRECT_BOOKING = "create_direct_booking"
    const val CANCEL_BOOKING = "cancel_booking"
    const val ACCEPT_BID = "accept_bid"
    const val CONFIRM_COMPLETION = "confirm_completion"
    const val REJECT_COMPLETION = "reject_completion"
    const val SUBMIT_REVIEW = "submit_review"
    // Worker
    const val PLACE_BID = "place_bid"
    const val ACCEPT_DIRECT_BOOKING = "accept_direct_booking"
    const val DECLINE_DIRECT_BOOKING = "decline_direct_booking"
    const val START_JOB = "start_job"
    const val COMPLETE_JOB = "complete_job"
    const val SET_AVAILABILITY = "set_availability"

    /** Action tools must be confirmed by the user before executing. */
    fun isActionTool(name: String): Boolean = name in setOf(
        // Customer write
        CREATE_BOOKING, CREATE_DIRECT_BOOKING, CANCEL_BOOKING, ACCEPT_BID,
        CONFIRM_COMPLETION, REJECT_COMPLETION, SUBMIT_REVIEW,
        // Worker write
        PLACE_BID, ACCEPT_DIRECT_BOOKING, DECLINE_DIRECT_BOOKING,
        START_JOB, COMPLETE_JOB, SET_AVAILABILITY
    )

    fun toolsFor(role: UserRole): List<GroqTool> = when (role) {
        UserRole.WORKER -> buildList {
            // Read
            add(getOpenRequests())
            add(getMyAnalytics())
            add(getMyBids())
            add(getPendingDirectBookings())
            add(getBookingStatus())
            add(getMyWallet())
            add(getMyWalletTransactions())
            add(getUnreadNotifications())
            add(getWorkerProfile())
            add(getWorkerReviews())
            add(openScreen())
            // Write
            add(placeBid())
            add(acceptDirectBooking())
            add(declineDirectBooking())
            add(startJob())
            add(completeJob())
            add(setAvailability())
        }
        UserRole.CUSTOMER -> buildList {
            // Read
            add(searchWorkers())
            add(getWorkerProfile())
            add(getWorkerReviews())
            add(getMyBookings())
            add(getBookingStatus())
            add(getBidsForBooking())
            add(getMyWallet())
            add(getMyWalletTransactions())
            add(getUnreadNotifications())
            add(openScreen())
            // Write
            add(createBooking())
            add(createDirectBooking())
            add(cancelBooking())
            add(acceptBid())
            add(confirmCompletion())
            add(rejectCompletion())
            add(submitReview())
        }
    }

    // ── Schema builder helpers ───────────────────────────────────────────────

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

    private fun booleanProp(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    // ── Read tool definitions ────────────────────────────────────────────────

    private fun searchWorkers() = GroqTool(
        function = GroqFunctionDef(
            name = SEARCH_WORKERS,
            description = "Tìm danh sách thợ dịch vụ đang sẵn sàng, có thể lọc theo danh mục, " +
                "giá tối đa mỗi giờ và đánh giá tối thiểu. Trả về cả workerId (UUID) để dùng " +
                "cho các thao tác tiếp theo (xem hồ sơ, đặt thợ trực tiếp).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("category", stringProp("Danh mục dịch vụ", enum = CATEGORIES))
                    put("maxPrice", numberProp("Giá tối đa mỗi giờ (VND)"))
                    put("minRating", numberProp("Đánh giá tối thiểu từ 0 đến 5"))
                }
                putJsonArray("required") {}
            }
        )
    )

    private fun getWorkerProfile() = GroqTool(
        function = GroqFunctionDef(
            name = GET_WORKER_PROFILE,
            description = "Xem chi tiết hồ sơ một thợ theo workerId (UUID): kỹ năng, " +
                "kinh nghiệm, đánh giá trung bình, giá theo giờ, trạng thái sẵn sàng.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("workerId", stringProp("Mã UUID của thợ"))
                }
                putJsonArray("required") { add("workerId") }
            }
        )
    )

    private fun getWorkerReviews() = GroqTool(
        function = GroqFunctionDef(
            name = GET_WORKER_REVIEWS,
            description = "Lấy danh sách đánh giá gần đây của một thợ.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("workerId", stringProp("Mã UUID của thợ"))
                }
                putJsonArray("required") { add("workerId") }
            }
        )
    )

    private fun getMyBookings() = GroqTool(
        function = GroqFunctionDef(
            name = GET_MY_BOOKINGS,
            description = "Lấy danh sách đơn đặt dịch vụ của khách hiện tại, có thể lọc theo " +
                "trạng thái. Trả về bookingId (UUID) cần dùng cho các thao tác tiếp theo.",
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
            description = "Xem chi tiết và trạng thái của một đơn cụ thể theo bookingId (UUID).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của đơn"))
                }
                putJsonArray("required") { add("bookingId") }
            }
        )
    )

    private fun getBidsForBooking() = GroqTool(
        function = GroqFunctionDef(
            name = GET_BIDS_FOR_BOOKING,
            description = "Liệt kê các báo giá đang có cho một đơn BIDDING của khách. " +
                "Trả về cả bidId (UUID) để khách có thể chấp nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của đơn BIDDING"))
                }
                putJsonArray("required") { add("bookingId") }
            }
        )
    )

    private fun getMyWallet() = GroqTool(
        function = GroqFunctionDef(
            name = GET_MY_WALLET,
            description = "Xem số dư ví của người dùng hiện tại: số dư khả dụng, số đang giữ, " +
                "tổng đã nhận / đã rút.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
                putJsonArray("required") {}
            }
        )
    )

    private fun getMyWalletTransactions() = GroqTool(
        function = GroqFunctionDef(
            name = GET_MY_WALLET_TRANSACTIONS,
            description = "Lịch sử giao dịch ví gần đây (nạp / rút / giữ / giải ngân).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("limit", numberProp("Số giao dịch tối đa (mặc định 10, tối đa 30)"))
                }
                putJsonArray("required") {}
            }
        )
    )

    private fun getUnreadNotifications() = GroqTool(
        function = GroqFunctionDef(
            name = GET_UNREAD_NOTIFICATIONS,
            description = "Đếm số thông báo chưa đọc và liệt kê vài thông báo gần đây.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
                putJsonArray("required") {}
            }
        )
    )

    private fun getOpenRequests() = GroqTool(
        function = GroqFunctionDef(
            name = GET_OPEN_REQUESTS,
            description = "Lấy danh sách yêu cầu công việc đang mở mà thợ có thể đặt giá " +
                "(đã lọc theo kỹ năng của thợ). Trả về bookingId (UUID) cần dùng cho place_bid.",
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
            description = "Thống kê thu nhập + hiệu suất của thợ hiện tại (tháng này, tổng, " +
                "số việc hoàn thành, đánh giá trung bình).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
                putJsonArray("required") {}
            }
        )
    )

    private fun getMyBids() = GroqTool(
        function = GroqFunctionDef(
            name = GET_MY_BIDS,
            description = "Liệt kê các báo giá thợ đã gửi (PENDING / ACCEPTED / REJECTED).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
                putJsonArray("required") {}
            }
        )
    )

    private fun getPendingDirectBookings() = GroqTool(
        function = GroqFunctionDef(
            name = GET_PENDING_DIRECT_BOOKINGS,
            description = "Đơn DIRECT đang chờ thợ chấp nhận (PENDING). Trả về bookingId (UUID) " +
                "để thợ accept hoặc decline.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
                putJsonArray("required") {}
            }
        )
    )

    private fun openScreen() = GroqTool(
        function = GroqFunctionDef(
            name = OPEN_SCREEN,
            description = "Mở một màn hình trong app để dẫn người dùng tới đúng nơi. " +
                "Khi route có {bookingId}, {workerId} hoặc {bidId}, thay bằng UUID thật. " +
                "Routes hợp lệ:\n" +
                "  • home, worker_home, chatbot\n" +
                "  • discover_workers, notification_list, notification_settings, help_support\n" +
                "  • customer_wallet, worker_wallet\n" +
                "  • worker_my_bids, worker_requests, worker_analytics, worker_reviews\n" +
                "  • worker_profile_edit, worker_verify_identity\n" +
                "  • customer_booking_detail/{bookingId}, worker_job_detail/{bookingId}\n" +
                "  • worker_navigation/{bookingId}, worker_public_profile/{workerId}\n" +
                "  • bidding_workers/{bookingId}, payment/{bookingId}\n" +
                "  • completion_confirm/{bookingId}, review/{bookingId}\n" +
                "  • booking/{categoryName}, booking/{categoryName}/{workerId}",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("route", stringProp("Route màn hình cần mở"))
                }
                putJsonArray("required") { add("route") }
            }
        )
    )

    // ── Action tool definitions ─────────────────────────────────────────────

    private fun createBooking() = GroqTool(
        function = GroqFunctionDef(
            name = CREATE_BOOKING,
            description = "Tạo đơn dịch vụ MỚI dạng BIDDING (nhiều thợ báo giá). " +
                "LUÔN cần xác nhận của khách trước khi gửi. Hãy hỏi đủ thông tin trước khi gọi: " +
                "danh mục, mô tả, địa chỉ, thời gian mong muốn.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("category", stringProp("Danh mục dịch vụ", enum = CATEGORIES))
                    put("description", stringProp("Mô tả công việc"))
                    put("address", stringProp("Địa chỉ"))
                    put("scheduledAt", stringProp(
                        "Thời gian khách muốn (ISO-8601 hoặc epoch millis). " +
                            "VD: '2026-06-08T09:00:00' hoặc 1717804800000"
                    ))
                    put("notes", stringProp("Ghi chú thêm (tùy chọn)"))
                }
                putJsonArray("required") {
                    add("category"); add("description"); add("address"); add("scheduledAt")
                }
            }
        )
    )

    private fun createDirectBooking() = GroqTool(
        function = GroqFunctionDef(
            name = CREATE_DIRECT_BOOKING,
            description = "Tạo đơn DIRECT đặt thẳng một thợ cụ thể. LUÔN cần xác nhận. " +
                "Cần workerId UUID lấy từ search_workers / get_worker_profile.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("workerId", stringProp("Mã UUID của thợ"))
                    put("category", stringProp("Danh mục dịch vụ", enum = CATEGORIES))
                    put("description", stringProp("Mô tả công việc"))
                    put("address", stringProp("Địa chỉ"))
                    put("scheduledAt", stringProp("Thời gian (ISO-8601 hoặc epoch millis)"))
                    put("notes", stringProp("Ghi chú thêm (tùy chọn)"))
                }
                putJsonArray("required") {
                    add("workerId"); add("category"); add("description"); add("address"); add("scheduledAt")
                }
            }
        )
    )

    private fun cancelBooking() = GroqTool(
        function = GroqFunctionDef(
            name = CANCEL_BOOKING,
            description = "Hủy một đơn của khách (chỉ khi đơn chưa hoàn thành). " +
                "LUÔN cần xác nhận. Lấy bookingId UUID từ get_my_bookings nếu chưa rõ.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của đơn"))
                    put("reason", stringProp("Lý do hủy (tùy chọn)"))
                }
                putJsonArray("required") { add("bookingId") }
            }
        )
    )

    private fun acceptBid() = GroqTool(
        function = GroqFunctionDef(
            name = ACCEPT_BID,
            description = "Khách chấp nhận một báo giá từ thợ → đơn chuyển sang AWAITING_PAYMENT. " +
                "LUÔN cần xác nhận. Lấy bidId UUID từ get_bids_for_booking.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bidId", stringProp("Mã UUID của báo giá"))
                }
                putJsonArray("required") { add("bidId") }
            }
        )
    )

    private fun confirmCompletion() = GroqTool(
        function = GroqFunctionDef(
            name = CONFIRM_COMPLETION,
            description = "Khách xác nhận đơn đã hoàn thành (chỉ khi đơn đang " +
                "PENDING_COMPLETION). Sẽ giải ngân tiền giữ cho thợ. LUÔN cần xác nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của đơn"))
                }
                putJsonArray("required") { add("bookingId") }
            }
        )
    )

    private fun rejectCompletion() = GroqTool(
        function = GroqFunctionDef(
            name = REJECT_COMPLETION,
            description = "Khách từ chối xác nhận hoàn thành (đơn quay về IN_PROGRESS). " +
                "LUÔN cần xác nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của đơn"))
                    put("reason", stringProp("Lý do từ chối (bắt buộc)"))
                }
                putJsonArray("required") { add("bookingId"); add("reason") }
            }
        )
    )

    private fun submitReview() = GroqTool(
        function = GroqFunctionDef(
            name = SUBMIT_REVIEW,
            description = "Gửi đánh giá cho thợ sau khi đơn hoàn thành. LUÔN cần xác nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID đơn đã hoàn thành"))
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
            description = "Đặt báo giá cho một yêu cầu công việc đang mở. LUÔN cần xác nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của yêu cầu công việc"))
                    put("price", numberProp("Giá đề xuất (VND)"))
                    put("durationHours", numberProp("Thời gian dự kiến (giờ)"))
                    put("message", stringProp("Lời giới thiệu / giải thích báo giá"))
                }
                putJsonArray("required") {
                    add("bookingId"); add("price"); add("durationHours"); add("message")
                }
            }
        )
    )

    private fun acceptDirectBooking() = GroqTool(
        function = GroqFunctionDef(
            name = ACCEPT_DIRECT_BOOKING,
            description = "Thợ chấp nhận đơn DIRECT đang ở trạng thái PENDING. LUÔN cần xác nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của đơn DIRECT/PENDING"))
                }
                putJsonArray("required") { add("bookingId") }
            }
        )
    )

    private fun declineDirectBooking() = GroqTool(
        function = GroqFunctionDef(
            name = DECLINE_DIRECT_BOOKING,
            description = "Thợ từ chối đơn DIRECT đang PENDING. LUÔN cần xác nhận. " +
                "Chỉ dùng cho đơn chưa được chấp nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của đơn DIRECT/PENDING"))
                    put("reason", stringProp("Lý do từ chối"))
                }
                putJsonArray("required") { add("bookingId"); add("reason") }
            }
        )
    )

    private fun startJob() = GroqTool(
        function = GroqFunctionDef(
            name = START_JOB,
            description = "Thợ bắt đầu công việc đã CONFIRMED → chuyển sang IN_PROGRESS. " +
                "LUÔN cần xác nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của đơn CONFIRMED"))
                }
                putJsonArray("required") { add("bookingId") }
            }
        )
    )

    private fun completeJob() = GroqTool(
        function = GroqFunctionDef(
            name = COMPLETE_JOB,
            description = "Thợ báo cáo đã hoàn thành công việc IN_PROGRESS → " +
                "chuyển sang PENDING_COMPLETION (chờ khách xác nhận). LUÔN cần xác nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("bookingId", stringProp("Mã UUID của đơn IN_PROGRESS"))
                    put("note", stringProp("Ghi chú hoàn thành (tùy chọn)"))
                }
                putJsonArray("required") { add("bookingId") }
            }
        )
    )

    private fun setAvailability() = GroqTool(
        function = GroqFunctionDef(
            name = SET_AVAILABILITY,
            description = "Bật / tắt trạng thái sẵn sàng nhận việc của thợ. LUÔN cần xác nhận.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("isAvailable", booleanProp("true = đang sẵn sàng, false = tạm nghỉ"))
                }
                putJsonArray("required") { add("isAvailable") }
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
