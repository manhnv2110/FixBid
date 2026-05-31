package com.example.fixbid.data.repository

import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.toFormattedDate
import com.example.fixbid.data.remote.groq.AiToolRegistry
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.usecase.customer.DiscoverWorkersUseCase
import com.example.fixbid.domain.usecase.customer.GetMyBookingsUseCase
import com.example.fixbid.domain.usecase.customer.SubmitReviewUseCase
import com.example.fixbid.domain.usecase.customer.WorkerSortBy
import com.example.fixbid.domain.usecase.worker.GetOpenJobRequestsUseCase
import com.example.fixbid.domain.usecase.worker.GetWorkerAnalyticsUseCase
import com.example.fixbid.domain.usecase.worker.PlaceBidUseCase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/** Result of running a tool: a navigation route may be surfaced to the UI. */
data class ToolRunResult(
    val resultJson: String,
    val navigationRoute: String? = null
)

/**
 * Executes an AI tool call by delegating to the existing use cases. Returns a
 * compact JSON string the LLM can use to compose its reply. Everything runs
 * under the current user's session, so Supabase RLS still applies — the
 * assistant can't exceed the user's own permissions.
 *
 * Read tools are executed during the model's tool loop. Action tools
 * (cancel/review/bid) are only executed here AFTER the user confirms in the UI.
 */
class AiToolExecutor @Inject constructor(
    private val discoverWorkersUseCase: DiscoverWorkersUseCase,
    private val getMyBookingsUseCase: GetMyBookingsUseCase,
    private val bookingRepository: BookingRepository,
    private val submitReviewUseCase: SubmitReviewUseCase,
    private val getOpenJobRequestsUseCase: GetOpenJobRequestsUseCase,
    private val getWorkerAnalyticsUseCase: GetWorkerAnalyticsUseCase,
    private val placeBidUseCase: PlaceBidUseCase
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun execute(name: String, args: JsonObject): ToolRunResult = when (name) {
        AiToolRegistry.SEARCH_WORKERS -> searchWorkers(args)
        AiToolRegistry.GET_MY_BOOKINGS -> getMyBookings(args)
        AiToolRegistry.GET_BOOKING_STATUS -> getBookingStatus(args)
        AiToolRegistry.GET_OPEN_REQUESTS -> getOpenRequests()
        AiToolRegistry.GET_MY_ANALYTICS -> getMyAnalytics()
        AiToolRegistry.OPEN_SCREEN -> openScreen(args)
        AiToolRegistry.CANCEL_BOOKING -> cancelBooking(args)
        AiToolRegistry.SUBMIT_REVIEW -> submitReview(args)
        AiToolRegistry.PLACE_BID -> placeBid(args)
        else -> ToolRunResult("""{"error":"Công cụ không được hỗ trợ: $name"}""")
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.num(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    // ── Read tools ──────────────────────────────────────────────────────────

    private suspend fun searchWorkers(args: JsonObject): ToolRunResult {
        val category = args.str("category")
            ?.let { runCatching { ServiceCategory.valueOf(it.uppercase()) }.getOrNull() }
        val maxPrice = args.num("maxPrice")
        val minRating = args.num("minRating")

        return when (val res = discoverWorkersUseCase(category, WorkerSortBy.RATING)) {
            is Resource.Success -> {
                val filtered = res.data.filter { w ->
                    (maxPrice == null || w.profile.pricePerHour <= maxPrice) &&
                        (minRating == null || w.profile.averageRating >= minRating)
                }.take(8)
                if (filtered.isEmpty()) {
                    ToolRunResult("""{"count":0,"message":"Không tìm thấy thợ phù hợp"}""")
                } else {
                    val items = filtered.joinToString(",") { w ->
                        """{"workerId":"${w.profile.userId}","name":"${w.displayName.jsonEscape()}",""" +
                            """"rating":${w.profile.averageRating},"reviews":${w.profile.totalReviews},""" +
                            """"jobsDone":${w.profile.totalJobsDone},"pricePerHour":"${formatCurrencyVnd(w.profile.pricePerHour)}",""" +
                            """"skills":"${w.profile.skills.joinToString(", ") { it.displayName }.jsonEscape()}"}"""
                    }
                    ToolRunResult("""{"count":${filtered.size},"workers":[$items]}""")
                }
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }
    }

    private suspend fun getMyBookings(args: JsonObject): ToolRunResult {
        val status = args.str("status")
            ?.let { runCatching { BookingStatus.valueOf(it.uppercase()) }.getOrNull() }

        return when (val res = getMyBookingsUseCase(status)) {
            is Resource.Success -> {
                val items = res.data.take(10).joinToString(",") { b ->
                    """{"bookingId":"${b.id}","category":"${b.category.displayName.jsonEscape()}",""" +
                        """"status":"${statusLabel(b.status)}","address":"${b.address.jsonEscape()}",""" +
                        """"scheduledAt":"${b.scheduledAt.toFormattedDate()}",""" +
                        """"price":"${b.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Chưa chốt"}"}"""
                }
                ToolRunResult("""{"count":${res.data.size},"bookings":[$items]}""")
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }
    }

    private suspend fun getBookingStatus(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")

        return when (val res = bookingRepository.getBookingById(bookingId)) {
            is Resource.Success -> {
                val b = res.data
                ToolRunResult(
                    """{"bookingId":"${b.id}","category":"${b.category.displayName.jsonEscape()}",""" +
                        """"status":"${statusLabel(b.status)}","address":"${b.address.jsonEscape()}",""" +
                        """"scheduledAt":"${b.scheduledAt.toFormattedDate()}",""" +
                        """"price":"${b.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Chưa chốt"}",""" +
                        """"description":"${b.description.jsonEscape()}"}"""
                )
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }
    }

    private suspend fun getOpenRequests(): ToolRunResult =
        when (val res = getOpenJobRequestsUseCase(applySkillsFilter = true)) {
            is Resource.Success -> {
                if (res.data.isEmpty()) {
                    ToolRunResult("""{"count":0,"message":"Hiện chưa có yêu cầu mở phù hợp"}""")
                } else {
                    val items = res.data.take(10).joinToString(",") { b ->
                        """{"bookingId":"${b.id}","category":"${b.category.displayName.jsonEscape()}",""" +
                            """"address":"${b.address.jsonEscape()}","scheduledAt":"${b.scheduledAt.toFormattedDate()}",""" +
                            """"budget":"${b.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Thoả thuận"}",""" +
                            """"description":"${b.description.jsonEscape()}"}"""
                    }
                    ToolRunResult("""{"count":${res.data.size},"requests":[$items]}""")
                }
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }

    private suspend fun getMyAnalytics(): ToolRunResult =
        when (val res = getWorkerAnalyticsUseCase()) {
            is Resource.Success -> {
                val a = res.data
                ToolRunResult(
                    """{"thisMonth":"${formatCurrencyVnd(a.thisMonthEarnings)}",""" +
                        """"lastMonth":"${formatCurrencyVnd(a.lastMonthEarnings)}",""" +
                        """"total":"${formatCurrencyVnd(a.totalEarnings)}",""" +
                        """"completedJobs":${a.completedJobs},"averagePerJob":"${formatCurrencyVnd(a.averagePerJob)}",""" +
                        """"rating":${a.averageRating},"totalReviews":${a.totalReviews}}"""
                )
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }

    private fun openScreen(args: JsonObject): ToolRunResult {
        val route = args.str("route")
            ?: return ToolRunResult("""{"error":"Thiếu route"}""")
        val allowed = route == "discover_workers" ||
            route == "notification_list" ||
            route == "home" ||
            route.startsWith("customer_booking_detail/") ||
            route.startsWith("worker_public_profile/")
        return if (allowed) {
            ToolRunResult("""{"opened":"$route"}""", navigationRoute = route)
        } else {
            ToolRunResult("""{"error":"Route không hợp lệ"}""")
        }
    }

    // ── Action tools (executed only after user confirmation) ─────────────────

    private suspend fun cancelBooking(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")
        val reason = args.str("reason") ?: "Khách hàng hủy qua trợ lý AI"
        return when (bookingRepository.cancelBooking(bookingId, reason)) {
            is Resource.Success -> ToolRunResult("""{"success":true,"message":"Đã hủy đơn thành công"}""")
            is Resource.Error -> ToolRunResult("""{"error":"mã đơn không hợp lệ hoặc không thể hủy"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun submitReview(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")
        val rating = args.num("rating")?.toInt()
            ?: return ToolRunResult("""{"error":"Thiếu số sao"}""")
        val comment = args.str("comment")

        // Resolve worker from the booking so the review is attributed correctly.
        val booking = (bookingRepository.getBookingById(bookingId) as? Resource.Success)?.data
            ?: return ToolRunResult("""{"error":"không tìm thấy đơn"}""")
        val workerId = booking.workerId.takeIf { it.isNotBlank() }
            ?: return ToolRunResult("""{"error":"đơn chưa có thợ để đánh giá"}""")

        return when (submitReviewUseCase(bookingId, workerId, rating, comment)) {
            is Resource.Success -> ToolRunResult("""{"success":true,"message":"Đã gửi đánh giá $rating sao"}""")
            is Resource.Error -> ToolRunResult("""{"error":"không gửi được đánh giá"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun placeBid(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã yêu cầu"}""")
        val price = args.num("price")
            ?: return ToolRunResult("""{"error":"Thiếu giá đề xuất"}""")
        val duration = args.num("durationHours") ?: 1.0
        val message = args.str("message") ?: "Tôi có thể nhận công việc này."

        return when (val res = placeBidUseCase(bookingId, price, duration, message)) {
            is Resource.Success -> ToolRunResult("""{"success":true,"message":"Đã gửi báo giá ${formatCurrencyVnd(price)}"}""")
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(60)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun statusLabel(status: BookingStatus): String = when (status) {
        BookingStatus.PENDING -> "Chờ xác nhận"
        BookingStatus.BIDDING -> "Đang nhận báo giá"
        BookingStatus.AWAITING_PAYMENT -> "Chờ thanh toán"
        BookingStatus.CONFIRMED -> "Đã xác nhận"
        BookingStatus.IN_PROGRESS -> "Đang thực hiện"
        BookingStatus.PENDING_COMPLETION -> "Chờ xác nhận hoàn thành"
        BookingStatus.COMPLETED -> "Hoàn thành"
        BookingStatus.CANCELLED -> "Đã hủy"
        BookingStatus.DISPUTED -> "Tranh chấp"
    }

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
