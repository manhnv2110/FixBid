// usecase/shared/MakePaymentUseCase.kt
package com.example.fixbid.domain.usecase.shared

import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentMethod
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.PaymentRepository
import javax.inject.Inject

class MakePaymentUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(
        bookingId: String,
        amount: Double,
        method: PaymentMethod
    ): Resource<Payment> {
        if (amount <= 0) return Resource.Error("Số tiền không hợp lệ")
        return paymentRepository.createPayment(bookingId, amount, method)
    }
}