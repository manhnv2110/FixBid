package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.PaymentRepository
import javax.inject.Inject

/**
 * Use case: Release escrow - chuyển tiền cho thợ khi khách xác nhận hoàn thành.
 *
 * Được gọi sau khi customer confirm completion.
 */
class ReleaseEscrowUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(bookingId: String): Resource<Payment> {
        return paymentRepository.releaseEscrow(bookingId)
    }
}