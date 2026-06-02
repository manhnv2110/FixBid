package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.PaymentRepository
import com.example.fixbid.domain.repository.WalletRepository
import javax.inject.Inject

/**
 * Use case: Release escrow — chuyển tiền cho thợ khi khách xác nhận hoàn thành.
 *
 * Two writes happen in sequence:
 *  1. `payments` row flips to `status=completed, escrow_status=released` so
 *     historical reporting & invoices stay accurate.
 *  2. The worker's wallet receives the money: `pending_balance --` and
 *     `balance ++`, plus a ledger row of type `escrow_release`. This part
 *     goes through a Postgres SECURITY DEFINER RPC so the balance + ledger
 *     mutation is atomic.
 *
 * If step 1 fails the second write is skipped (nothing to release).
 * If step 2 fails the booking still shows COMPLETED; the next time the
 * worker opens their app, [ReleasePendingEscrowsUseCase] will retry the
 * wallet movement (the RPC is idempotent on payment_id).
 */
class ReleaseEscrowUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(bookingId: String): Resource<Payment> {
        val paymentResult = paymentRepository.releaseEscrow(bookingId)
        if (paymentResult is Resource.Success) {
            // Bơm tiền sang ví. Nếu RPC fail (mạng kém chẳng hạn) thì coi
            // như recovery — hệ thống sẽ retry sau nên không bao giờ mất.
            runCatching { walletRepository.releaseEscrow(paymentResult.data.id) }
        }
        return paymentResult
    }
}
