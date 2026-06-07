package com.example.fixbid.domain.notification

import com.example.fixbid.domain.model.NotificationContent
import com.example.fixbid.domain.model.NotificationType

/**
 * Single source of truth for notification copy (tiếng Việt). Keeping the strings
 * here means every place that emits a notification — customer flows, worker
 * flows, schedulers — renders consistent, production-quality wording.
 */
object NotificationContentFactory {

    private const val APP = "FixBid"

    /** Thợ nhận được một yêu cầu công việc mới (đặt trực tiếp hoặc mời thầu). */
    fun bookingRequestForWorker(
        workerId: String,
        bookingId: String,
        categoryName: String
    ) = NotificationContent(
        recipientUserId = workerId,
        title = "Yêu cầu công việc mới",
        body = "Bạn có một yêu cầu \"$categoryName\" mới. Xem chi tiết và phản hồi ngay.",
        type = NotificationType.BOOKING_REQUEST,
        referenceId = bookingId
    )

    /** Khách được báo thợ đã xác nhận / booking đã được xác nhận. */
    fun bookingConfirmedForCustomer(
        customerId: String,
        bookingId: String,
        categoryName: String
    ) = NotificationContent(
        recipientUserId = customerId,
        title = "Đặt lịch đã được xác nhận",
        body = "Dịch vụ \"$categoryName\" của bạn đã được xác nhận. Thợ sẽ đến đúng lịch hẹn.",
        type = NotificationType.BOOKING_CONFIRMED,
        referenceId = bookingId
    )

    fun bookingCancelledForUser(
        userId: String,
        bookingId: String,
        categoryName: String,
        reason: String?
    ) = NotificationContent(
        recipientUserId = userId,
        title = "Lịch hẹn đã bị hủy",
        body = buildString {
            append("Dịch vụ \"$categoryName\" đã bị hủy.")
            if (!reason.isNullOrBlank()) append(" Lý do: $reason")
        },
        type = NotificationType.BOOKING_CANCELLED,
        referenceId = bookingId
    )

    /**
     * Khách được báo thợ đã chấp nhận đơn đặt trực tiếp — bước tiếp theo là thanh toán.
     * Khác với [bookingConfirmedForCustomer] (phát sau khi thanh toán xong) để copy nói rõ
     * action kế tiếp.
     */
    fun directBookingAcceptedForCustomer(
        customerId: String,
        bookingId: String,
        categoryName: String,
        workerName: String?
    ) = NotificationContent(
        recipientUserId = customerId,
        title = "Thợ đã nhận đơn",
        body = buildString {
            append(workerName?.takeIf { it.isNotBlank() } ?: "Thợ")
            append(" đã chấp nhận yêu cầu \"$categoryName\". ")
            append("Vui lòng thanh toán để xác nhận lịch hẹn.")
        },
        type = NotificationType.BOOKING_CONFIRMED,
        referenceId = bookingId
    )

    // ── Direct-booking quote flow ────────────────────────────────────────────
    // Khi khách đặt trực tiếp một thợ, thợ phải báo giá trước khi khách thanh
    // toán. 3 hàm dưới đây phát thông báo cho mỗi bước (báo giá / chấp nhận /
    // từ chối) — mỗi thông báo đều dẫn về màn chi tiết booking để user có thể
    // thực hiện tiếp action mong muốn.

    /** Khách được báo thợ vừa gửi báo giá cho đơn đặt trực tiếp. */
    fun directBookingQuotedForCustomer(
        customerId: String,
        bookingId: String,
        categoryName: String,
        workerName: String?,
        priceLabel: String
    ) = NotificationContent(
        recipientUserId = customerId,
        title = "Thợ đã báo giá",
        body = buildString {
            append(workerName?.takeIf { it.isNotBlank() } ?: "Thợ")
            append(" báo giá $priceLabel cho yêu cầu \"$categoryName\". ")
            append("Xem và chọn chấp nhận hoặc yêu cầu báo lại.")
        },
        type = NotificationType.BOOKING_QUOTED,
        referenceId = bookingId
    )

    /** Thợ được báo khách đã chấp nhận báo giá — đang chờ khách thanh toán. */
    fun directQuoteAcceptedForWorker(
        workerId: String,
        bookingId: String,
        categoryName: String
    ) = NotificationContent(
        recipientUserId = workerId,
        title = "Khách đã chấp nhận báo giá",
        body = "Khách đã đồng ý báo giá cho \"$categoryName\". Hệ thống đang chờ khách thanh toán để bạn có thể bắt đầu công việc.",
        type = NotificationType.BOOKING_QUOTE_ACCEPTED,
        referenceId = bookingId
    )

    /** Thợ được báo khách từ chối báo giá — có thể gửi lại với mức khác. */
    fun directQuoteRejectedForWorker(
        workerId: String,
        bookingId: String,
        categoryName: String,
        reason: String?
    ) = NotificationContent(
        recipientUserId = workerId,
        title = "Khách từ chối báo giá",
        body = buildString {
            append("Khách chưa đồng ý báo giá cho \"$categoryName\".")
            if (!reason.isNullOrBlank()) append(" Lý do: $reason")
            append(" Bạn có thể gửi báo giá khác hoặc từ chối đơn.")
        },
        type = NotificationType.BOOKING_QUOTE_REJECTED,
        referenceId = bookingId
    )

    /** Khách được báo thợ từ chối đơn đặt trực tiếp. */
    fun directBookingDeclinedForCustomer(
        customerId: String,
        bookingId: String,
        categoryName: String,
        reason: String?
    ) = NotificationContent(
        recipientUserId = customerId,
        title = "Thợ không thể nhận đơn",
        body = buildString {
            append("Yêu cầu \"$categoryName\" của bạn không được thợ nhận.")
            if (!reason.isNullOrBlank()) append(" Lý do: $reason")
            append(" Bạn có thể thử thợ khác hoặc đăng yêu cầu mở.")
        },
        type = NotificationType.BOOKING_CANCELLED,
        referenceId = bookingId
    )

    /**
     * Khách được báo thợ đã chủ động hủy đơn sau khi đã thanh toán — kèm xác nhận
     * số tiền đã được hoàn vào ví FixBid và lý do thợ hủy.
     */
    fun bookingCancelledByWorkerForCustomer(
        customerId: String,
        bookingId: String,
        categoryName: String,
        refundAmountLabel: String,
        reason: String
    ) = NotificationContent(
        recipientUserId = customerId,
        title = "Thợ đã hủy đơn",
        body = "Đơn \"$categoryName\" đã được hoàn $refundAmountLabel vào ví FixBid. Lý do: $reason",
        type = NotificationType.BOOKING_CANCELLED,
        referenceId = bookingId
    )

    /**
     * Thợ được xác nhận đã hủy đơn thành công — kèm số tiền đã bị trừ khỏi ví và
     * hoàn cho khách.
     */
    fun bookingCancelledByWorkerForWorker(
        workerId: String,
        bookingId: String,
        categoryName: String,
        deductedAmountLabel: String
    ) = NotificationContent(
        recipientUserId = workerId,
        title = "Đã hủy đơn thành công",
        body = "Đơn \"$categoryName\" đã hủy. $deductedAmountLabel đã được hoàn cho khách và trừ khỏi ví của bạn.",
        type = NotificationType.BOOKING_CANCELLED,
        referenceId = bookingId
    )

    /** Nhắc lịch hẹn sắp tới (cleaning schedule reminder). */
    fun bookingReminderForUser(
        userId: String,
        bookingId: String,
        categoryName: String,
        whenLabel: String
    ) = NotificationContent(
        recipientUserId = userId,
        title = "Nhắc lịch hẹn sắp tới",
        body = "Dịch vụ \"$categoryName\" của bạn sẽ bắt đầu $whenLabel. Hãy chuẩn bị sẵn sàng nhé.",
        type = NotificationType.BOOKING_REMINDER,
        referenceId = bookingId
    )

    /** Khách nhận được một báo giá mới từ thợ. */
    fun bidReceivedForCustomer(
        customerId: String,
        bookingId: String,
        workerName: String,
        priceLabel: String
    ) = NotificationContent(
        recipientUserId = customerId,
        title = "Báo giá mới",
        body = "$workerName vừa gửi báo giá $priceLabel cho yêu cầu của bạn. Xem và chọn thợ ngay.",
        type = NotificationType.BID_RECEIVED,
        referenceId = bookingId
    )

    /** Thợ được báo báo giá của mình đã được khách chọn. */
    fun bidAcceptedForWorker(
        workerId: String,
        bookingId: String,
        categoryName: String
    ) = NotificationContent(
        recipientUserId = workerId,
        title = "Báo giá được chấp nhận",
        body = "Chúc mừng! Báo giá của bạn cho công việc \"$categoryName\" đã được chọn.",
        type = NotificationType.BID_ACCEPTED,
        referenceId = bookingId
    )

    /** Khách được báo thợ đang trên đường đến. */
    fun workerOnTheWayForCustomer(
        customerId: String,
        bookingId: String,
        workerName: String
    ) = NotificationContent(
        recipientUserId = customerId,
        title = "Thợ đang trên đường",
        body = "$workerName đang di chuyển đến địa chỉ của bạn.",
        type = NotificationType.WORKER_ON_THE_WAY,
        referenceId = bookingId
    )

    /** Khách được báo thợ đã bắt đầu công việc. */
    fun jobStartedForCustomer(
        customerId: String,
        bookingId: String,
        categoryName: String
    ) = NotificationContent(
        recipientUserId = customerId,
        title = "Đã bắt đầu công việc",
        body = "Thợ đã bắt đầu thực hiện dịch vụ \"$categoryName\".",
        type = NotificationType.JOB_STARTED,
        referenceId = bookingId
    )

    /** Khách được báo thợ đã hoàn thành công việc, chờ xác nhận. */
    fun jobCompletedForCustomer(
        customerId: String,
        bookingId: String,
        categoryName: String
    ) = NotificationContent(
        recipientUserId = customerId,
        title = "Công việc đã hoàn thành",
        body = "Thợ báo đã hoàn thành \"$categoryName\". Vui lòng kiểm tra và xác nhận.",
        type = NotificationType.JOB_COMPLETED,
        referenceId = bookingId
    )

    /** Thợ được báo khách đã xác nhận hoàn thành. */
    fun completionConfirmedForWorker(
        workerId: String,
        bookingId: String,
        categoryName: String
    ) = NotificationContent(
        recipientUserId = workerId,
        title = "Khách đã xác nhận hoàn thành",
        body = "Khách hàng đã xác nhận hoàn thành \"$categoryName\". Cảm ơn bạn đã làm việc tốt!",
        type = NotificationType.JOB_COMPLETED,
        referenceId = bookingId
    )

    /** Thợ được báo khách yêu cầu làm lại (từ chối hoàn thành). */
    fun completionRejectedForWorker(
        workerId: String,
        bookingId: String,
        reason: String?
    ) = NotificationContent(
        recipientUserId = workerId,
        title = "Khách yêu cầu làm lại",
        body = buildString {
            append("Khách hàng chưa hài lòng và yêu cầu tiếp tục công việc.")
            if (!reason.isNullOrBlank()) append(" Lý do: $reason")
        },
        type = NotificationType.JOB_STARTED,
        referenceId = bookingId
    )

    fun paymentReceivedForWorker(
        workerId: String,
        bookingId: String,
        amountLabel: String
    ) = NotificationContent(
        recipientUserId = workerId,
        title = "Đã nhận thanh toán",
        body = "Bạn đã nhận được $amountLabel cho công việc vừa hoàn thành.",
        type = NotificationType.PAYMENT_RECEIVED,
        referenceId = bookingId
    )

    /** Thợ được báo khách vừa để lại một đánh giá mới. */
    fun newReviewForWorker(
        workerId: String,
        bookingId: String,
        rating: Int,
        customerName: String?
    ) = NotificationContent(
        recipientUserId = workerId,
        title = "Bạn nhận được đánh giá mới",
        body = buildString {
            append(customerName?.takeIf { it.isNotBlank() } ?: "Khách hàng")
            append(" đã đánh giá $rating★")
            append(" cho công việc của bạn.")
        },
        type = NotificationType.NEW_REVIEW,
        referenceId = bookingId
    )

    /**
     * Người dùng nhận được một tin nhắn mới trong cuộc trò chuyện 1-1.
     *
     * - [referenceId] là `conversationId` (không phải `messageId`) — khi user
     *   tap notification, deep-link vào màn hình chat tương ứng.
     * - [preview] là nội dung gọn (đã trim/clip) hiển thị trong body. Nếu là
     *   ảnh, truyền sẵn "📷 Đã gửi 1 ảnh".
     */
    fun newMessageForRecipient(
        recipientId: String,
        conversationId: String,
        senderName: String,
        preview: String
    ) = NotificationContent(
        recipientUserId = recipientId,
        title = senderName.ifBlank { "Tin nhắn mới" },
        body = preview.trim().ifBlank { "Đã gửi tin nhắn" }.take(140),
        type = NotificationType.NEW_MESSAGE,
        referenceId = conversationId
    )
}
