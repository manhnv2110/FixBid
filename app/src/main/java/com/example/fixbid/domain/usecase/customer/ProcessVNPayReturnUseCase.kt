package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.data.remote.vnpay.VNPayService
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import javax.inject.Inject

/**
 * Use case: Xử lý callback từ VNPay sau khi thanh toán.
 *
 * Flow:
 * 1. Verify chữ ký response
 * 2. Kiểm tra mã phản hồi (00 = thành công)
 * 3. Cập nhật payment status -> ESCROW (holding)
 * 4. Cập nhật booking status -> CONFIRMED
 * 5. Hold worker_receives vào pending_balance của ví thợ
 */
class ProcessVNPayReturnUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val bookingRepository: BookingRepository,
    private val walletRepository: com.example.fixbid.domain.repository.WalletRepository,
    private val vnPayService: VNPayService
) {
    suspend operator fun invoke(params: Map<String, String>): Resource<Payment> {
        // 1. Verify chữ ký
        if (!vnPayService.verifyReturnUrl(params)) {
            return Resource.Error("Chữ ký không hợp lệ. Giao dịch có thể bị giả mạo.")
        }

        val responseCode = params["vnp_ResponseCode"]
        val transactionId = params["vnp_TransactionNo"] ?: ""
        val paymentId = params["vnp_TxnRef"] ?: ""

        // 2. Kiểm tra mã phản hồi
        if (!vnPayService.isPaymentSuccess(responseCode)) {
            return Resource.Error(getErrorMessage(responseCode))
        }

        // 3. Cập nhật payment -> ESCROW (hệ thống giữ tiền)
        val updateResult = paymentRepository.updatePaymentToEscrow(
            paymentId = paymentId,
            transactionId = transactionId
        )

        return when (updateResult) {
            is Resource.Success -> {
                // 4. Cập nhật booking -> CONFIRMED (thợ có thể bắt đầu công việc)
                val bookingId = updateResult.data.bookingId
                bookingRepository.confirmBooking(bookingId)
                // 5. Bơm worker_receives sang pending_balance của ví thợ.
                //    RPC idempotent: nếu IPN + return URL chạy hai lần thì
                //    cũng chỉ insert một dòng wallet_transactions.
                runCatching { walletRepository.holdEscrow(updateResult.data.id) }
                updateResult
            }
            is Resource.Error -> updateResult
            Resource.Loading -> Resource.Loading
        }
    }

    private fun getErrorMessage(responseCode: String?): String {
        return when (responseCode) {
            "07" -> "Trừ tiền thành công nhưng giao dịch bị nghi ngờ (liên quan tới lừa đảo, giao dịch bất thường)"
            "09" -> "Thẻ/Tài khoản chưa đăng ký dịch vụ InternetBanking"
            "10" -> "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần"
            "11" -> "Đã hết hạn chờ thanh toán. Vui lòng thực hiện lại giao dịch."
            "12" -> "Thẻ/Tài khoản bị khóa"
            "13" -> "Nhập sai mật khẩu xác thực (OTP). Vui lòng thực hiện lại."
            "24" -> "Khách hàng hủy giao dịch"
            "51" -> "Tài khoản không đủ số dư để thực hiện giao dịch"
            "65" -> "Tài khoản đã vượt quá hạn mức giao dịch trong ngày"
            "75" -> "Ngân hàng thanh toán đang bảo trì"
            "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định"
            "99" -> "Lỗi không xác định"
            else -> "Thanh toán thất bại (Mã: $responseCode)"
        }
    }
}