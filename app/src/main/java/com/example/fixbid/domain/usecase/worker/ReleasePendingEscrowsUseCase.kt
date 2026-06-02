package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.EscrowStatus
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import com.example.fixbid.core.utils.formatCurrencyVnd
import javax.inject.Inject

/**
 * Idempotent recovery for the stuck-escrow edge case.
 *
 * Why this exists: when the customer taps "Xác nhận hoàn thành",
 * `CompletionConfirmViewModel` first marks the booking as COMPLETED, then
 * calls `releaseEscrow`. Those are two separate writes — if the second one
 * fails (network drop, transient DB error) the booking is COMPLETED but the
 * payment row is still `escrow / holding`, and the worker never sees the
 * money land.
 *
 * Running this use case on every worker app open finds those stragglers
 * (booking.status = COMPLETED && payment.escrowStatus = HOLDING) and
 * re-issues the release. Because [PaymentRepository.releaseEscrow] just
 * sets fixed columns, calling it twice on an already-released payment is a
 * harmless no-op.
 */
class ReleasePendingEscrowsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val walletRepository: com.example.fixbid.domain.repository.WalletRepository,
    private val sendNotification: SendNotificationUseCase
) {

    /** Returns the number of stuck escrows that were successfully released. */
    suspend operator fun invoke(): Int {
        val workerId = authRepository.getCurrentUser()?.id ?: return 0

        val payments = (paymentRepository.getPaymentHistory(workerId) as? Resource.Success)
            ?.data ?: return 0

        val stuck = payments.filter { it.isStuckEscrow() }
        if (stuck.isEmpty()) return 0

        var releasedCount = 0
        stuck.forEach { payment ->
            // Only release once the underlying booking has actually moved to
            // COMPLETED. Anything else means the customer hasn't confirmed
            // yet and we shouldn't pre-empt them.
            val booking = (bookingRepository.getBookingById(payment.bookingId) as? Resource.Success)
                ?.data ?: return@forEach
            if (booking.status != BookingStatus.COMPLETED) return@forEach

            when (val release = paymentRepository.releaseEscrow(payment.bookingId)) {
                is Resource.Success -> {
                    releasedCount++
                    // Đẩy thêm sang ví — RPC idempotent nên không sợ double-credit.
                    runCatching { walletRepository.releaseEscrow(release.data.id) }
                    sendNotification(
                        NotificationContentFactory.paymentReceivedForWorker(
                            workerId = workerId,
                            bookingId = payment.bookingId,
                            amountLabel = formatCurrencyVnd(release.data.workerReceives)
                        )
                    )
                }
                else -> Unit
            }
        }
        return releasedCount
    }

    private fun Payment.isStuckEscrow(): Boolean =
        status == PaymentStatus.ESCROW || escrowStatus == EscrowStatus.HOLDING
}
