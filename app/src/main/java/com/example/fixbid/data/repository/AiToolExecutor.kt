package com.example.fixbid.data.repository

import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.toFormattedDate
import com.example.fixbid.data.remote.groq.AiToolRegistry
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.BookingType
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.model.WalletTransactionType
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BidRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.NotificationRepository
import com.example.fixbid.domain.repository.ReviewRepository
import com.example.fixbid.domain.repository.WalletRepository
import com.example.fixbid.domain.repository.WorkerRepository
import com.example.fixbid.domain.usecase.customer.AcceptBidUseCase
import com.example.fixbid.domain.usecase.customer.ConfirmCompletionUseCase
import com.example.fixbid.domain.usecase.customer.CreateBookingUseCase
import com.example.fixbid.domain.usecase.customer.DiscoverWorkersUseCase
import com.example.fixbid.domain.usecase.customer.GetMyBookingsUseCase
import com.example.fixbid.domain.usecase.customer.SubmitReviewUseCase
import com.example.fixbid.domain.usecase.customer.WorkerSortBy
import com.example.fixbid.domain.usecase.worker.AcceptDirectBookingUseCase
import com.example.fixbid.domain.usecase.worker.DeclineDirectBookingUseCase
import com.example.fixbid.domain.usecase.worker.GetOpenJobRequestsUseCase
import com.example.fixbid.domain.usecase.worker.GetWorkerAnalyticsUseCase
import com.example.fixbid.domain.usecase.worker.PlaceBidUseCase
import com.example.fixbid.domain.usecase.worker.UpdateJobStatusUseCase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/** Result of running a tool: a navigation route may be surfaced to the UI. */
data class ToolRunResult(
    val resultJson: String,
    val navigationRoute: String? = null
)

/**
 * Executes an AI tool call by delegating to the existing use cases / repositories.
 * Returns a compact JSON string the LLM can use to compose its reply. Everything
 * runs under the current user's session, so Supabase RLS still applies — the
 * assistant can't exceed the user's own permissions.
 *
 * Read tools are executed during the model's tool loop. Action tools are only
 * executed here AFTER the user explicitly confirms in the UI (see
 * [AiAgentRepositoryImpl.confirmAction]).
 */
class AiToolExecutor @Inject constructor(
    // Customer tools
    private val discoverWorkersUseCase: DiscoverWorkersUseCase,
    private val getMyBookingsUseCase: GetMyBookingsUseCase,
    private val createBookingUseCase: CreateBookingUseCase,
    private val acceptBidUseCase: AcceptBidUseCase,
    private val confirmCompletionUseCase: ConfirmCompletionUseCase,
    private val submitReviewUseCase: SubmitReviewUseCase,
    // Worker tools
    private val getOpenJobRequestsUseCase: GetOpenJobRequestsUseCase,
    private val getWorkerAnalyticsUseCase: GetWorkerAnalyticsUseCase,
    private val placeBidUseCase: PlaceBidUseCase,
    private val acceptDirectBookingUseCase: AcceptDirectBookingUseCase,
    private val declineDirectBookingUseCase: DeclineDirectBookingUseCase,
    private val updateJobStatusUseCase: UpdateJobStatusUseCase,
    // Repositories used directly for read-style operations
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val bidRepository: BidRepository,
    private val workerRepository: WorkerRepository,
    private val reviewRepository: ReviewRepository,
    private val walletRepository: WalletRepository,
    private val notificationRepository: NotificationRepository,
    private val toolCache: AiToolCache
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun execute(name: String, args: JsonObject): ToolRunResult {
        // Read tools hit the 30s LRU cache so repeated lookups in one
        // conversation don't burn extra Groq tokens or Supabase round-trips.
        // Action / write tools always run live + invalidate the cache so the
        // next read sees fresh state.
        if (shouldCache(name)) {
            toolCache.get(name, args)?.let { return it }
            val fresh = executeUncached(name, args)
            toolCache.put(name, args, fresh)
            return fresh
        }
        val result = executeUncached(name, args)
        if (AiToolRegistry.isActionTool(name)) toolCache.invalidate()
        return result
    }

    /** Read tools whose output we can safely re-use within [AiToolCache.TTL]. */
    private fun shouldCache(name: String): Boolean = name in setOf(
        AiToolRegistry.SEARCH_WORKERS, AiToolRegistry.GET_WORKER_PROFILE,
        AiToolRegistry.GET_WORKER_REVIEWS, AiToolRegistry.GET_MY_BOOKINGS,
        AiToolRegistry.GET_BOOKING_STATUS, AiToolRegistry.GET_BIDS_FOR_BOOKING,
        AiToolRegistry.GET_OPEN_REQUESTS, AiToolRegistry.GET_MY_ANALYTICS,
        AiToolRegistry.GET_MY_BIDS, AiToolRegistry.GET_PENDING_DIRECT_BOOKINGS,
        AiToolRegistry.GET_MY_WALLET, AiToolRegistry.GET_MY_WALLET_TRANSACTIONS,
        AiToolRegistry.GET_UNREAD_NOTIFICATIONS
    )

    private suspend fun executeUncached(name: String, args: JsonObject): ToolRunResult = when (name) {
        // ── Read ──────────────────────────────────────────────────────────
        AiToolRegistry.SEARCH_WORKERS -> searchWorkers(args)
        AiToolRegistry.GET_WORKER_PROFILE -> getWorkerProfile(args)
        AiToolRegistry.GET_WORKER_REVIEWS -> getWorkerReviews(args)
        AiToolRegistry.GET_MY_BOOKINGS -> getMyBookings(args)
        AiToolRegistry.GET_BOOKING_STATUS -> getBookingStatus(args)
        AiToolRegistry.GET_BIDS_FOR_BOOKING -> getBidsForBooking(args)
        AiToolRegistry.GET_OPEN_REQUESTS -> getOpenRequests()
        AiToolRegistry.GET_MY_ANALYTICS -> getMyAnalytics()
        AiToolRegistry.GET_MY_BIDS -> getMyBids()
        AiToolRegistry.GET_PENDING_DIRECT_BOOKINGS -> getPendingDirectBookings()
        AiToolRegistry.GET_MY_WALLET -> getMyWallet()
        AiToolRegistry.GET_MY_WALLET_TRANSACTIONS -> getMyWalletTransactions(args)
        AiToolRegistry.GET_UNREAD_NOTIFICATIONS -> getUnreadNotifications()
        AiToolRegistry.OPEN_SCREEN -> openScreen(args)

        // ── Action ────────────────────────────────────────────────────────
        AiToolRegistry.CREATE_BOOKING -> createBooking(args, direct = false)
        AiToolRegistry.CREATE_DIRECT_BOOKING -> createBooking(args, direct = true)
        AiToolRegistry.CANCEL_BOOKING -> cancelBooking(args)
        AiToolRegistry.ACCEPT_BID -> acceptBid(args)
        AiToolRegistry.CONFIRM_COMPLETION -> confirmCompletion(args)
        AiToolRegistry.REJECT_COMPLETION -> rejectCompletion(args)
        AiToolRegistry.SUBMIT_REVIEW -> submitReview(args)
        AiToolRegistry.PLACE_BID -> placeBid(args)
        AiToolRegistry.ACCEPT_DIRECT_BOOKING -> acceptDirectBooking(args)
        AiToolRegistry.DECLINE_DIRECT_BOOKING -> declineDirectBooking(args)
        AiToolRegistry.START_JOB -> startJob(args)
        AiToolRegistry.COMPLETE_JOB -> completeJob(args)
        AiToolRegistry.SET_AVAILABILITY -> setAvailability(args)

        else -> ToolRunResult("""{"error":"Công cụ không được hỗ trợ: $name"}""")
    }

    // ── Argument helpers ─────────────────────────────────────────────────────

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.num(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    /** Parse the model's `scheduledAt` argument — accepts ISO-8601 or epoch millis. */
    private fun parseScheduledAt(raw: String): Long? {
        // 1. Pure number → epoch millis straight away.
        raw.toLongOrNull()?.let { return it }
        // 2. ISO-8601: try OffsetDateTime, LocalDateTime, LocalDate.
        return runCatching {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(raw, java.time.OffsetDateTime::from)
                .toInstant().toEpochMilli()
        }.recoverCatching {
            LocalDateTime.parse(raw)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.recoverCatching {
            // Fallback: appended T00:00:00 if the user gave a bare date.
            LocalDateTime.parse("${raw}T00:00:00")
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

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
                            """"jobsDone":${w.profile.totalJobsDone},""" +
                            """"pricePerHour":"${formatCurrencyVnd(w.profile.pricePerHour)}",""" +
                            """"skills":"${w.profile.skills.joinToString(", ") { it.displayName }.jsonEscape()}",""" +
                            """"available":${w.profile.isAvailable}}"""
                    }
                    ToolRunResult("""{"count":${filtered.size},"workers":[$items]}""")
                }
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }
    }

    private suspend fun getWorkerProfile(args: JsonObject): ToolRunResult {
        val workerId = args.str("workerId")
            ?: return ToolRunResult("""{"error":"Thiếu workerId"}""")
        if (!isUuid(workerId)) return ToolRunResult("""{"error":"workerId không phải UUID"}""")

        return when (val res = workerRepository.getWorkerById(workerId)) {
            is Resource.Success -> {
                val p = res.data
                ToolRunResult(
                    """{"workerId":"${p.userId}","skills":"${p.skills.joinToString(", ") { it.displayName }.compact(120)}",""" +
                        """"experienceYears":${p.experienceYears},""" +
                        """"pricePerHour":"${formatCurrencyVnd(p.pricePerHour)}",""" +
                        """"location":"${p.location.compact(60)}",""" +
                        """"available":${p.isAvailable},"rating":${p.averageRating},""" +
                        """"totalReviews":${p.totalReviews},"totalJobsDone":${p.totalJobsDone},""" +
                        """"verified":${p.identityVerified},"bio":"${p.bio.compact(160)}"}"""
                )
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }
    }

    private suspend fun getWorkerReviews(args: JsonObject): ToolRunResult {
        val workerId = args.str("workerId")
            ?: return ToolRunResult("""{"error":"Thiếu workerId"}""")
        if (!isUuid(workerId)) return ToolRunResult("""{"error":"workerId không phải UUID"}""")

        return when (val res = reviewRepository.getReviewsForWorker(workerId)) {
            is Resource.Success -> {
                if (res.data.isEmpty()) {
                    ToolRunResult("""{"count":0,"message":"Thợ này chưa có đánh giá nào"}""")
                } else {
                    val items = res.data.take(8).joinToString(",") { r ->
                        """{"rating":${r.rating},"comment":"${(r.comment ?: "").jsonEscape()}",""" +
                            """"createdAt":"${r.createdAt.toFormattedDate()}"}"""
                    }
                    val avg = res.data.map { it.rating }.average()
                    ToolRunResult("""{"count":${res.data.size},"average":${"%.1f".format(avg)},"reviews":[$items]}""")
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
                        """"status":"${statusLabel(b.status)}","statusCode":"${b.status.name}",""" +
                        """"address":"${b.address.jsonEscape()}",""" +
                        """"scheduledAt":"${b.scheduledAt.toFormattedDate()}",""" +
                        """"price":"${b.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Chưa chốt"}",""" +
                        """"workerName":"${(b.worker?.fullName ?: "").jsonEscape()}"}"""
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
        if (!isUuid(bookingId)) return ToolRunResult("""{"error":"bookingId không phải UUID"}""")

        return when (val res = bookingRepository.getBookingById(bookingId)) {
            is Resource.Success -> {
                val b = res.data
                ToolRunResult(
                    """{"bookingId":"${b.id}","category":"${b.category.displayName.jsonEscape()}",""" +
                        """"status":"${statusLabel(b.status)}","statusCode":"${b.status.name}",""" +
                        """"type":"${b.type.name}","address":"${b.address.compact(80)}",""" +
                        """"scheduledAt":"${b.scheduledAt.toFormattedDate()}",""" +
                        """"price":"${b.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Chưa chốt"}",""" +
                        """"description":"${b.description.compact(160)}",""" +
                        """"workerName":"${(b.worker?.fullName ?: "").compact(40)}",""" +
                        """"customerName":"${(b.customer?.fullName ?: "").compact(40)}"}"""
                )
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }
    }

    private suspend fun getBidsForBooking(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu bookingId"}""")
        if (!isUuid(bookingId)) return ToolRunResult("""{"error":"bookingId không phải UUID"}""")

        return when (val res = bidRepository.getBidsForBooking(bookingId)) {
            is Resource.Success -> {
                if (res.data.isEmpty()) {
                    ToolRunResult("""{"count":0,"message":"Đơn này chưa có báo giá nào"}""")
                } else {
                    val items = res.data.take(10).joinToString(",") { b ->
                        """{"bidId":"${b.id}","workerId":"${b.workerId}",""" +
                            """"workerName":"${(b.worker?.fullName ?: "").jsonEscape()}",""" +
                            """"price":"${formatCurrencyVnd(b.proposedPrice)}",""" +
                            """"durationHours":${b.estimatedDurationHours},""" +
                            """"message":"${b.message.jsonEscape()}",""" +
                            """"status":"${b.status.name}"}"""
                    }
                    ToolRunResult("""{"count":${res.data.size},"bids":[$items]}""")
                }
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
                            """"address":"${b.address.jsonEscape()}",""" +
                            """"scheduledAt":"${b.scheduledAt.toFormattedDate()}",""" +
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
                        """"completedJobs":${a.completedJobs},""" +
                        """"averagePerJob":"${formatCurrencyVnd(a.averagePerJob)}",""" +
                        """"rating":${a.averageRating},"totalReviews":${a.totalReviews}}"""
                )
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }

    private suspend fun getMyBids(): ToolRunResult {
        val user = authRepository.getCurrentUser()
            ?: return ToolRunResult("""{"error":"Chưa đăng nhập"}""")
        return when (val res = bidRepository.getMyBids(user.id)) {
            is Resource.Success -> {
                if (res.data.isEmpty()) {
                    ToolRunResult("""{"count":0,"message":"Bạn chưa gửi báo giá nào"}""")
                } else {
                    val items = res.data.take(10).joinToString(",") { b ->
                        """{"bidId":"${b.id}","bookingId":"${b.bookingId}",""" +
                            """"price":"${formatCurrencyVnd(b.proposedPrice)}",""" +
                            """"durationHours":${b.estimatedDurationHours},""" +
                            """"status":"${b.status.name}"}"""
                    }
                    ToolRunResult("""{"count":${res.data.size},"bids":[$items]}""")
                }
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }
    }

    private suspend fun getPendingDirectBookings(): ToolRunResult {
        val user = authRepository.getCurrentUser()
            ?: return ToolRunResult("""{"error":"Chưa đăng nhập"}""")
        return when (val res = bookingRepository.getPendingDirectBookings(user.id)) {
            is Resource.Success -> {
                if (res.data.isEmpty()) {
                    ToolRunResult("""{"count":0,"message":"Không có đơn đặt trực tiếp đang chờ"}""")
                } else {
                    val items = res.data.take(10).joinToString(",") { b ->
                        """{"bookingId":"${b.id}","category":"${b.category.displayName.jsonEscape()}",""" +
                            """"address":"${b.address.compact(60)}",""" +
                            """"scheduledAt":"${b.scheduledAt.toFormattedDate()}",""" +
                            """"customerName":"${(b.customer?.fullName ?: "").compact(40)}",""" +
                            """"description":"${b.description.compact(120)}"}"""
                    }
                    ToolRunResult("""{"count":${res.data.size},"requests":[$items]}""")
                }
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }
    }

    private suspend fun getMyWallet(): ToolRunResult =
        when (val res = walletRepository.getMyWallet()) {
            is Resource.Success -> {
                val w = res.data
                ToolRunResult(
                    """{"balance":"${formatCurrencyVnd(w.balance)}",""" +
                        """"pendingBalance":"${formatCurrencyVnd(w.pendingBalance)}",""" +
                        """"totalEarned":"${formatCurrencyVnd(w.totalEarned)}",""" +
                        """"totalWithdrawn":"${formatCurrencyVnd(w.totalWithdrawn)}"}"""
                )
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }

    private suspend fun getMyWalletTransactions(args: JsonObject): ToolRunResult {
        val limit = args.num("limit")?.toInt()?.coerceIn(1, 30) ?: 10
        return when (val res = walletRepository.getMyTransactions(limit)) {
            is Resource.Success -> {
                if (res.data.isEmpty()) {
                    ToolRunResult("""{"count":0,"message":"Chưa có giao dịch nào"}""")
                } else {
                    val items = res.data.take(limit).joinToString(",") { t ->
                        """{"type":"${transactionLabel(t.type)}",""" +
                            """"amount":"${formatCurrencyVnd(t.amount)}",""" +
                            """"createdAt":"${t.createdAt.toFormattedDate()}",""" +
                            """"description":"${(t.description ?: "").jsonEscape()}"}"""
                    }
                    ToolRunResult("""{"count":${res.data.size},"transactions":[$items]}""")
                }
            }
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape()}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang tải"}""")
        }
    }

    private suspend fun getUnreadNotifications(): ToolRunResult {
        val user = authRepository.getCurrentUser()
            ?: return ToolRunResult("""{"error":"Chưa đăng nhập"}""")
        val countRes = notificationRepository.getUnreadCount(user.id)
        val count = (countRes as? Resource.Success)?.data ?: 0
        return when (val res = notificationRepository.getNotifications(user.id)) {
            is Resource.Success -> {
                val unread = res.data.filter { !it.isRead }.take(5)
                val items = unread.joinToString(",") { n ->
                    """{"title":"${n.title.jsonEscape()}","body":"${n.body.jsonEscape()}",""" +
                        """"type":"${n.type.name}",""" +
                        """"referenceId":"${n.referenceId ?: ""}",""" +
                        """"createdAt":"${n.createdAt.toFormattedDate()}"}"""
                }
                ToolRunResult("""{"unreadCount":$count,"items":[$items]}""")
            }
            is Resource.Error -> ToolRunResult("""{"unreadCount":$count,"items":[]}""")
            is Resource.Loading -> ToolRunResult("""{"unreadCount":$count,"items":[]}""")
        }
    }

    private fun openScreen(args: JsonObject): ToolRunResult {
        val route = args.str("route")
            ?: return ToolRunResult("""{"error":"Thiếu route"}""")
        return if (isAllowedRoute(route)) {
            ToolRunResult("""{"opened":"$route"}""", navigationRoute = route)
        } else {
            ToolRunResult("""{"error":"Route không hợp lệ"}""")
        }
    }

    /** Whitelist mirroring [com.example.fixbid.MainActivity]'s NavHost. */
    private fun isAllowedRoute(route: String): Boolean {
        // Fixed routes
        val fixed = setOf(
            "home", "worker_home", "chatbot",
            "discover_workers", "notification_list", "notification_settings",
            "help_support", "customer_wallet", "worker_wallet",
            "worker_my_bids", "worker_requests", "worker_analytics", "worker_reviews",
            "worker_profile_edit", "worker_verify_identity", "worker_chat_list"
        )
        if (route in fixed) return true
        // Parameterised routes — guard the trailing segment as either a UUID or a
        // ServiceCategory enum name so we never blow up on garbage routes.
        return when {
            route.startsWith("customer_booking_detail/") -> isUuidSuffix(route)
            route.startsWith("worker_job_detail/") -> isUuidSuffix(route)
            route.startsWith("worker_navigation/") -> isUuidSuffix(route)
            route.startsWith("worker_public_profile/") -> isUuidSuffix(route)
            route.startsWith("bidding_workers/") -> isUuidSuffix(route)
            route.startsWith("payment/") -> isUuidSuffix(route)
            route.startsWith("completion_confirm/") -> isUuidSuffix(route)
            route.startsWith("review/") -> isUuidSuffix(route)
            route.startsWith("booking/") -> isCategoryRoute(route)
            else -> false
        }
    }

    private fun isUuidSuffix(route: String): Boolean {
        val tail = route.substringAfterLast('/')
        return isUuid(tail)
    }

    private fun isCategoryRoute(route: String): Boolean {
        // booking/{categoryName} OR booking/{categoryName}/{workerId}
        val parts = route.removePrefix("booking/").split('/')
        if (parts.isEmpty() || parts.size > 2) return false
        val categoryOk = runCatching { ServiceCategory.valueOf(parts[0].uppercase()) }.isSuccess
        val workerOk = parts.size == 1 || isUuid(parts[1])
        return categoryOk && workerOk
    }

    // ── Action tools (only run after user confirmation) ─────────────────────

    private suspend fun createBooking(args: JsonObject, direct: Boolean): ToolRunResult {
        val user = authRepository.getCurrentUser()
            ?: return ToolRunResult("""{"error":"Chưa đăng nhập"}""")
        val categoryName = args.str("category")
            ?: return ToolRunResult("""{"error":"Thiếu category"}""")
        val category = runCatching { ServiceCategory.valueOf(categoryName.uppercase()) }.getOrNull()
            ?: return ToolRunResult("""{"error":"Danh mục không hợp lệ"}""")
        val description = args.str("description")
            ?: return ToolRunResult("""{"error":"Thiếu mô tả"}""")
        val address = args.str("address")
            ?: return ToolRunResult("""{"error":"Thiếu địa chỉ"}""")
        val scheduledAtRaw = args.str("scheduledAt") ?: args.long("scheduledAt")?.toString()
            ?: return ToolRunResult("""{"error":"Thiếu thời gian (scheduledAt)"}""")
        val scheduledAt = parseScheduledAt(scheduledAtRaw)
            ?: return ToolRunResult("""{"error":"Thời gian không hợp lệ"}""")
        if (scheduledAt < System.currentTimeMillis() - 60_000) {
            return ToolRunResult("""{"error":"Thời gian phải ở tương lai"}""")
        }

        val workerId = if (direct) {
            args.str("workerId")
                ?: return ToolRunResult("""{"error":"Thiếu workerId"}""")
        } else ""
        if (direct && !isUuid(workerId)) {
            return ToolRunResult("""{"error":"workerId không phải UUID"}""")
        }

        val customerNote = buildString {
            append("SĐT: ${user.phoneNumber ?: ""}")
            append("\nTên: ${user.fullName}")
            val notes = args.str("notes") ?: ""
            if (notes.isNotBlank()) {
                append("\nGhi chú: Tạo qua trợ lý AI. $notes")
            } else {
                append("\nGhi chú: Tạo qua trợ lý AI")
            }
        }

        val now = System.currentTimeMillis()
        val booking = Booking(
            id = UUID.randomUUID().toString(),
            customerId = user.id,
            workerId = workerId,
            category = category,
            description = description,
            address = address,
            latitude = null,
            longitude = null,
            scheduledAt = scheduledAt,
            estimatedDurationHours = 1.0,
            status = if (direct) BookingStatus.PENDING else BookingStatus.BIDDING,
            type = if (direct) BookingType.DIRECT else BookingType.BIDDING,
            agreedPrice = null,
            customerNote = customerNote,
            workerNote = null,
            createdAt = now,
            updatedAt = now
        )

        return when (val res = createBookingUseCase(booking)) {
            is Resource.Success -> ToolRunResult(
                """{"success":true,"bookingId":"${res.data.id}","message":"Đã tạo đơn ${category.displayName}"}""",
                navigationRoute = "customer_booking_detail/${res.data.id}"
            )
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

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

    private suspend fun acceptBid(args: JsonObject): ToolRunResult {
        val bidId = args.str("bidId")
            ?: return ToolRunResult("""{"error":"Thiếu bidId"}""")
        return when (val res = acceptBidUseCase(bidId)) {
            is Resource.Success -> ToolRunResult(
                """{"success":true,"message":"Đã chấp nhận báo giá. Vui lòng tiến hành thanh toán."}""",
                navigationRoute = "payment/${res.data.bookingId}"
            )
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun confirmCompletion(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")
        return when (val res = confirmCompletionUseCase.confirm(bookingId)) {
            is Resource.Success -> ToolRunResult(
                """{"success":true,"message":"Đã xác nhận hoàn thành. Mời bạn để lại đánh giá!"}""",
                navigationRoute = "review/$bookingId"
            )
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun rejectCompletion(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")
        val reason = args.str("reason")
            ?: return ToolRunResult("""{"error":"Thiếu lý do"}""")
        return when (val res = confirmCompletionUseCase.reject(bookingId, reason)) {
            is Resource.Success -> ToolRunResult("""{"success":true,"message":"Đã gửi từ chối hoàn thành."}""")
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun submitReview(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")
        val rating = args.num("rating")?.toInt()
            ?: return ToolRunResult("""{"error":"Thiếu số sao"}""")
        val comment = args.str("comment")

        val booking = (bookingRepository.getBookingById(bookingId) as? Resource.Success)?.data
            ?: return ToolRunResult("""{"error":"không tìm thấy đơn"}""")
        val workerId = booking.workerId.takeIf { it.isNotBlank() }
            ?: return ToolRunResult("""{"error":"đơn chưa có thợ để đánh giá"}""")

        return when (submitReviewUseCase(bookingId, workerId, rating, comment)) {
            is Resource.Success -> ToolRunResult(
                """{"success":true,"message":"Đã gửi đánh giá $rating sao. Cảm ơn bạn!"}"""
            )
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
            is Resource.Success -> ToolRunResult(
                """{"success":true,"message":"Đã gửi báo giá ${formatCurrencyVnd(price)}"}"""
            )
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun acceptDirectBooking(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")
        return when (val res = acceptDirectBookingUseCase(bookingId)) {
            is Resource.Success -> ToolRunResult(
                """{"success":true,"message":"Đã chấp nhận đơn. Khách sẽ thanh toán để xác nhận."}""",
                navigationRoute = "worker_job_detail/${res.data.id}"
            )
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun declineDirectBooking(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")
        val reason = args.str("reason")
            ?: return ToolRunResult("""{"error":"Thiếu lý do từ chối"}""")
        return when (val res = declineDirectBookingUseCase(bookingId, reason)) {
            is Resource.Success -> ToolRunResult("""{"success":true,"message":"Đã từ chối đơn."}""")
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun startJob(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")
        return when (val res = updateJobStatusUseCase(bookingId, BookingStatus.IN_PROGRESS)) {
            is Resource.Success -> ToolRunResult(
                """{"success":true,"message":"Đã bắt đầu công việc"}""",
                navigationRoute = "worker_job_detail/$bookingId"
            )
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun completeJob(args: JsonObject): ToolRunResult {
        val bookingId = args.str("bookingId")
            ?: return ToolRunResult("""{"error":"Thiếu mã đơn"}""")
        val note = args.str("note")
        return when (val res = updateJobStatusUseCase(bookingId, BookingStatus.PENDING_COMPLETION, note)) {
            is Resource.Success -> ToolRunResult(
                """{"success":true,"message":"Đã báo hoàn thành. Chờ khách xác nhận."}"""
            )
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    private suspend fun setAvailability(args: JsonObject): ToolRunResult {
        val isAvailable = args.bool("isAvailable")
            ?: return ToolRunResult("""{"error":"Thiếu trạng thái sẵn sàng"}""")
        return when (val res = workerRepository.setAvailability(isAvailable)) {
            is Resource.Success -> ToolRunResult(
                """{"success":true,"message":"${if (isAvailable) "Bạn đang hiển thị là sẵn sàng nhận việc" else "Đã chuyển sang trạng thái nghỉ"}"}"""
            )
            is Resource.Error -> ToolRunResult("""{"error":"${res.message.jsonEscape().take(80)}"}""")
            is Resource.Loading -> ToolRunResult("""{"error":"đang xử lý"}""")
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun statusLabel(status: BookingStatus): String = when (status) {
        BookingStatus.PENDING -> "Chờ xác nhận"
        BookingStatus.BIDDING -> "Đang nhận báo giá"
        BookingStatus.QUOTED -> "Chờ duyệt báo giá"
        BookingStatus.AWAITING_PAYMENT -> "Chờ thanh toán"
        BookingStatus.CONFIRMED -> "Đã xác nhận"
        BookingStatus.IN_PROGRESS -> "Đang thực hiện"
        BookingStatus.PENDING_COMPLETION -> "Chờ xác nhận hoàn thành"
        BookingStatus.COMPLETED -> "Hoàn thành"
        BookingStatus.CANCELLED -> "Đã hủy"
        BookingStatus.DISPUTED -> "Tranh chấp"
    }

    private fun transactionLabel(type: WalletTransactionType): String = when (type) {
        WalletTransactionType.ESCROW_HOLD -> "Giữ tiền"
        WalletTransactionType.ESCROW_RELEASE -> "Giải ngân"
        WalletTransactionType.ESCROW_REFUND -> "Hoàn tiền"
        WalletTransactionType.WITHDRAWAL -> "Rút tiền"
        WalletTransactionType.WITHDRAWAL_REQUEST -> "Yêu cầu rút"
        WalletTransactionType.TOPUP -> "Nạp tiền"
        WalletTransactionType.ADJUSTMENT -> "Điều chỉnh"
    }

    private fun isUuid(value: String): Boolean = UUID_REGEX.matches(value)

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    /** Clip + escape long free-text fields so tool results stay token-cheap. */
    private fun String.compact(maxLen: Int = 80): String =
        take(maxLen).jsonEscape()

    private companion object {
        val UUID_REGEX = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
