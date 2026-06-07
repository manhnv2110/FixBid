package com.example.fixbid.data.repository

import com.example.fixbid.core.di.ApplicationScope
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.NotificationType
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.NotificationRepository
import com.example.fixbid.domain.repository.ProactivePrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens to fresh server-pushed notifications and converts the actionable
 * ones into [ProactivePrompt]s the chatbot can surface as suggestion chips.
 *
 * Lifecycle:
 *  - One singleton per process. Started by [start] from a session-aware
 *    callsite (see `AppNotificationsViewModel.init`) and never stopped — it
 *    naturally completes when the application scope dies.
 *  - The output stream is a hot [SharedFlow] with a small replay so the
 *    chatbot can render the most recent prompt even if it opens after the
 *    notification arrived.
 *
 * Why not `StateFlow<List<ProactivePrompt>>`? The chatbot wants a
 * fire-and-forget notification primitive, not a list to render. SharedFlow
 * with replay=1 is the natural fit.
 */
@Singleton
class ProactiveAssistantCoordinator @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository
) {

    private val _prompts = MutableSharedFlow<ProactivePrompt>(
        replay = 1,
        extraBufferCapacity = 4
    )
    val prompts: SharedFlow<ProactivePrompt> = _prompts.asSharedFlow()

    /** Notification ids whose prompts were already emitted this process. */
    private val seenIds = java.util.Collections.synchronizedSet(linkedSetOf<String>())

    private var started = false

    /** Idempotent — safe to call from multiple ViewModels. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val user = authRepository.getCurrentUser() ?: return@launch
            val role = user.role
            notificationRepository.observeNewNotifications(user.id).collect { n ->
                // Dedupe so a re-subscription (e.g. process resurrection) won't
                // re-emit prompts for the same notification rows.
                if (!seenIds.add(n.id)) return@collect
                // Cap to prevent unbounded growth — keep only the most recent
                // 100 ids; older notifications can be safely re-promoted if
                // they show up again.
                if (seenIds.size > 100) {
                    synchronized(seenIds) {
                        val iter = seenIds.iterator()
                        repeat(seenIds.size - 100) {
                            if (iter.hasNext()) { iter.next(); iter.remove() }
                        }
                    }
                }
                buildPrompt(n, role)?.let { _prompts.emit(it) }
            }
        }
    }

    /**
     * Map a [Notification] to a [ProactivePrompt] tailored to the recipient's
     * role. Returns null when the notification isn't actionable inside the
     * chatbot (e.g. SYSTEM, generic chat messages).
     */
    private fun buildPrompt(n: Notification, role: UserRole): ProactivePrompt? {
        val short = n.referenceId?.take(8)?.uppercase().orEmpty()
        val (suggestion, body) = when (n.type) {
            NotificationType.BID_RECEIVED -> {
                if (role != UserRole.CUSTOMER) return null
                ("Tóm tắt các báo giá cho đơn $short giúp tôi" to
                    "Có báo giá mới cho đơn của bạn. Cần mình tóm tắt nhanh các báo giá không?")
            }
            NotificationType.BID_ACCEPTED -> {
                if (role != UserRole.WORKER) return null
                ("Báo giá của tôi cho đơn $short được chấp nhận, giờ phải làm gì?" to
                    "Tin tốt: báo giá của bạn đã được khách chấp nhận. Mình hướng dẫn các bước tiếp theo nhé?")
            }
            NotificationType.BOOKING_REQUEST -> {
                if (role != UserRole.WORKER) return null
                ("Có yêu cầu đặt trực tiếp $short, gợi ý mình nên làm gì" to
                    "Bạn vừa có một đơn đặt trực tiếp. Cần mình tóm tắt yêu cầu và gợi ý phản hồi không?")
            }
            NotificationType.WORKER_ON_THE_WAY -> {
                if (role != UserRole.CUSTOMER) return null
                ("Thợ đang trên đường tới đơn $short, mình cần chuẩn bị gì?" to
                    "Thợ đang trên đường tới. Mình gợi ý vài thứ cần chuẩn bị nhé?")
            }
            NotificationType.JOB_STARTED -> {
                if (role != UserRole.CUSTOMER) return null
                ("Thợ đã bắt đầu làm đơn $short, theo dõi giúp mình" to
                    "Thợ đã bắt đầu công việc. Cần mình theo dõi tiến độ không?")
            }
            NotificationType.JOB_COMPLETED -> {
                if (role != UserRole.CUSTOMER) return null
                ("Thợ báo hoàn thành đơn $short, giúp mình kiểm tra trước khi xác nhận" to
                    "Thợ vừa báo hoàn thành. Cần mình tóm tắt các điểm cần kiểm tra không?")
            }
            NotificationType.PAYMENT_RECEIVED -> {
                if (role != UserRole.WORKER) return null
                ("Cập nhật ví và thu nhập tháng này giúp mình" to
                    "Bạn vừa nhận thanh toán. Cần xem ví và thống kê tháng không?")
            }
            NotificationType.NEW_REVIEW -> {
                if (role != UserRole.WORKER) return null
                ("Có đánh giá mới — đọc và gợi ý mình cách phản hồi" to
                    "Bạn có đánh giá mới. Mình đọc và gợi ý phản hồi nhé?")
            }
            NotificationType.BOOKING_REMINDER -> {
                ("Nhắc giúp mình lịch hẹn sắp tới $short" to
                    "Sắp đến lịch hẹn. Cần mình tóm tắt thông tin không?")
            }
            // Skip noisy / non-conversational types.
            NotificationType.NEW_MESSAGE,
            NotificationType.BOOKING_CONFIRMED,
            NotificationType.BOOKING_CANCELLED,
            NotificationType.SYSTEM -> return null
        }
        return ProactivePrompt(
            id = n.id,
            title = n.title.takeIf { it.isNotBlank() } ?: defaultTitle(n.type),
            body = body,
            suggestion = suggestion,
            sourceType = n.type,
            createdAt = n.createdAt
        )
    }

    private fun defaultTitle(type: NotificationType): String = when (type) {
        NotificationType.BID_RECEIVED -> "Có báo giá mới"
        NotificationType.BID_ACCEPTED -> "Báo giá được chấp nhận"
        NotificationType.BOOKING_REQUEST -> "Đơn đặt trực tiếp"
        NotificationType.WORKER_ON_THE_WAY -> "Thợ đang đến"
        NotificationType.JOB_STARTED -> "Bắt đầu công việc"
        NotificationType.JOB_COMPLETED -> "Báo hoàn thành"
        NotificationType.PAYMENT_RECEIVED -> "Đã nhận thanh toán"
        NotificationType.NEW_REVIEW -> "Có đánh giá mới"
        NotificationType.BOOKING_REMINDER -> "Nhắc lịch hẹn"
        else -> "Trợ lý gợi ý"
    }
}
