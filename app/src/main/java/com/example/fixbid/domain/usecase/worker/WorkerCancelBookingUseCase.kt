package com.example.fixbid.domain.usecase.worker

import android.util.Log
import com.example.fixbid.core.di.ApplicationScope
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.EscrowStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import com.example.fixbid.domain.repository.WalletRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Worker cancels a CONFIRMED booking after the customer has already paid.
 *
 * Sequence (see design.md "Sequence Diagram: Worker Cancels CONFIRMED Booking"):
 *   1. Validate the cancel reason (trimmed length ≥ 10).
 *   2. Load the booking.
 *   3. Guard: status == CONFIRMED AND workerId == auth.currentUserId().
 *   4. If a HOLDING payment exists, call the refund RPC. RPC failure = abort
 *      before touching the booking — no partial state.
 *   5. Update the booking row (status, cancel_reason, updated_at).
 *   6. Build the updated booking snapshot and return Success **before** awaiting
 *      notifications.
 *   7. Fire-and-forget two notification sends on [notificationScope] — failures
 *      are logged but never roll back the cancel/refund.
 *
 * Partial-recovery branch (RPC ok, booking update failed) is logged with
 * [Log.e] so ops/Crashlytics can pick it up; the customer has already been
 * refunded so the financial state is correct, but the booking row may briefly
 * still show CONFIRMED until a future reload/cron flips it.
 */
class WorkerCancelBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val walletRepository: WalletRepository,
    private val authRepository: AuthRepository,
    private val sendNotification: SendNotificationUseCase,
    @ApplicationScope private val notificationScope: CoroutineScope
) {
    suspend operator fun invoke(
        bookingId: String,
        cancelReason: String
    ): Resource<Booking> {
        // 1. Reason guard — cheap, no I/O.
        val trimmedReason = cancelReason.trim()
        if (trimmedReason.length < 10) {
            return Resource.Error("Lý do hủy phải có ít nhất 10 ký tự")
        }

        // 2. Load booking.
        val booking = when (val result = bookingRepository.getBookingById(bookingId)) {
            is Resource.Success -> result.data
            is Resource.Error -> return result
            Resource.Loading -> return Resource.Error("Không tải được đơn")
        }

        // 3. State & ownership guard.
        val currentUserId = authRepository.getCurrentUser()?.id
        if (booking.status != BookingStatus.CONFIRMED ||
            booking.workerId != currentUserId
        ) {
            return Resource.Error("Không thể hủy đơn ở trạng thái này")
        }

        // 4. Refund the held escrow if applicable. Skip silently when there is
        //    no payment or when the escrow has already been released/refunded.
        val payment = (paymentRepository.getPaymentByBooking(bookingId)
            as? Resource.Success)?.data
        if (payment != null && payment.escrowStatus == EscrowStatus.HOLDING) {
            when (val rpc = walletRepository.refundEscrowToCustomer(payment.id)) {
                is Resource.Error -> return Resource.Error(rpc.message)
                is Resource.Success -> Unit
                Resource.Loading -> Unit
            }
        }

        // 5. Update the booking row. If this fails after a successful refund
        //    we land in the partial-recovery branch documented in design.md.
        when (val cancelResult = bookingRepository.cancelBooking(bookingId, trimmedReason)) {
            is Resource.Error -> {
                Log.e(
                    TAG,
                    "Refund succeeded but booking update failed: ${cancelResult.message}"
                )
                return cancelResult
            }
            is Resource.Success -> Unit
            Resource.Loading -> return Resource.Error("Hủy đơn thất bại")
        }

        // 6. Build the post-cancel snapshot. cancelBooking() returns Unit, so
        //    we synthesise the updated Booking from the loaded one — only the
        //    status/cancel_reason/updated_at columns change per Req 9.1–9.3.
        val updatedBooking = booking.copy(
            status = BookingStatus.CANCELLED,
            cancelReason = trimmedReason,
            updatedAt = System.currentTimeMillis()
        )

        // 7. Fire-and-forget notifications — launched on the injected
        //    application scope so they outlive this use case's caller and
        //    never roll back the cancel on failure (Req 4.10, 4.11).
        val refundAmountLabel = formatCurrencyVnd(payment?.amount ?: 0.0)
        val deductedAmountLabel = formatCurrencyVnd(payment?.workerReceives ?: 0.0)
        val categoryName = booking.category.displayName

        notificationScope.launch {
            runCatching {
                sendNotification(
                    NotificationContentFactory.bookingCancelledByWorkerForCustomer(
                        customerId = booking.customerId,
                        bookingId = booking.id,
                        categoryName = categoryName,
                        refundAmountLabel = refundAmountLabel,
                        reason = trimmedReason
                    )
                )
            }.onFailure { err ->
                Log.e(TAG, "Refund succeeded but notification failed", err)
            }
        }

        notificationScope.launch {
            runCatching {
                sendNotification(
                    NotificationContentFactory.bookingCancelledByWorkerForWorker(
                        workerId = booking.workerId,
                        bookingId = booking.id,
                        categoryName = categoryName,
                        deductedAmountLabel = deductedAmountLabel
                    )
                )
            }.onFailure { err ->
                Log.e(TAG, "Refund succeeded but notification failed", err)
            }
        }

        return Resource.Success(updatedBooking)
    }

    private companion object {
        const val TAG = "WorkerCancelBooking"
    }
}
