package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.data.remote.vnpay.VNPayService
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentMethod
import com.example.fixbid.domain.model.PaymentStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.PaymentRepository
import javax.inject.Inject

/**
 * Use case: Tạo payment record + generate VNPay URL.
 *
 * Flow:
 * 1. Kiểm tra xem đã có payment PENDING cho booking này chưa
 * 2. Nếu chưa → tạo payment record mới (status = PENDING)
 * 3. Generate VNPay payment URL với paymentId làm orderId
 * 4. Return URL để customer redirect sang VNPay
 */
class CreateVNPayPaymentUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val vnPayService: VNPayService
) {
    data class VNPayResult(
        val payment: Payment,
        val paymentUrl: String
    )

    suspend operator fun invoke(
        bookingId: String,
        amount: Double
    ): Resource<VNPayResult> {
        if (amount <= 0) return Resource.Error("Số tiền không hợp lệ")

        // 1. Kiểm tra xem đã có payment cho booking này chưa
        val existingPaymentResult = paymentRepository.getPaymentByBooking(bookingId)
        val existingPayment = (existingPaymentResult as? Resource.Success)?.data

        val payment: Payment

        if (existingPayment != null && existingPayment.status == PaymentStatus.PENDING) {
            // Đã có payment PENDING → dùng lại, chỉ generate URL mới
            payment = existingPayment
        } else if (existingPayment != null) {
            // Payment đã ở trạng thái khác (ESCROW, COMPLETED, etc.)
            return Resource.Error("Đơn hàng này đã được thanh toán")
        } else {
            // Chưa có payment → tạo mới
            val createResult = paymentRepository.createPayment(
                bookingId = bookingId,
                amount = amount,
                method = PaymentMethod.VNPAY
            )
            when (createResult) {
                is Resource.Success -> payment = createResult.data
                is Resource.Error -> return Resource.Error(createResult.message)
                Resource.Loading -> return Resource.Loading
            }
        }

        // 2. Generate VNPay URL
        val paymentUrl = vnPayService.createPaymentUrl(
            orderId = payment.id,
            amount = amount.toLong(),
            orderInfo = "Thanh toan FixBid - Don hang ${payment.id.take(8)}"
        )

        return Resource.Success(VNPayResult(payment = payment, paymentUrl = paymentUrl))
    }
}