package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.data.remote.vnpay.VNPayService
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import com.example.fixbid.domain.repository.WalletRepository
import javax.inject.Inject

/**
 * Use case: Xử lý callback từ VNPay sau khi thanh toán.
 *
 * Hai luồng chạy chung route deep link `fixbid://vnpay-return`:
 *
 *  - **Booking payment** — `vnp_TxnRef` là raw payment id. Chuyển payment
 *    sang ESCROW + booking sang CONFIRMED + hold escrow vào ví thợ.
 *  - **Wallet top-up** — `vnp_TxnRef` có prefix `TOPUP-` (xem
 *    [CreateWalletTopupUseCase.TOPUP_TXN_REF_PREFIX]). Cộng tiền vào ví khách
 *    qua `fn_credit_wallet_topup`.
 *
 * Sealed [Result] phơi rõ luồng nào vừa hoàn tất để UI điều hướng đúng nơi
 * (ví khách vs trang chủ).
 */
class ProcessVNPayReturnUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val bookingRepository: BookingRepository,
    private val walletRepository: WalletRepository,
    private val vnPayService: VNPayService
) {
    sealed class Result {
        data class BookingPayment(val paymentId: String, val bookingId: String) : Result()
        data class WalletTopup(val vnpTxnRef: String, val balanceAfter: Double?) : Result()
    }

    suspend operator fun invoke(params: Map<String, String>): Resource<Result> {
        // 1. Verify chữ ký
        if (!vnPayService.verifyReturnUrl(params)) {
            return Resource.Error("Chữ ký không hợp lệ. Giao dịch có thể bị giả mạo.")
        }

        val responseCode = params["vnp_ResponseCode"]
        val transactionId = params["vnp_TransactionNo"] ?: ""
        val txnRef = params["vnp_TxnRef"] ?: ""

        if (txnRef.isBlank()) {
            return Resource.Error("Thiếu mã giao dịch (vnp_TxnRef)")
        }

        // ── Wallet top-up branch ───────────────────────────────────────────
        // The gateway echoes back exactly the vnp_TxnRef we shipped — that's
        // also the value stored as `wallet_topups.vnp_txn_ref`, so we look up
        // by the full ref (not by stripping the prefix).
        if (txnRef.startsWith(CreateWalletTopupUseCase.TOPUP_TXN_REF_PREFIX)) {
            if (!vnPayService.isPaymentSuccess(responseCode)) {
                walletRepository.failWalletTopup(txnRef, responseCode ?: "unknown")
                return Resource.Error(getErrorMessage(responseCode))
            }

            return when (val r = walletRepository.creditWalletTopup(txnRef, transactionId)) {
                is Resource.Success -> Resource.Success(
                    Result.WalletTopup(vnpTxnRef = txnRef, balanceAfter = r.data.balance)
                )
                is Resource.Error -> Resource.Error(r.message)
                Resource.Loading -> Resource.Loading
            }
        }

        // ── Booking payment branch (existing flow) ─────────────────────────
        val paymentId = txnRef
        if (!vnPayService.isPaymentSuccess(responseCode)) {
            return Resource.Error(getErrorMessage(responseCode))
        }

        val updateResult = paymentRepository.updatePaymentToEscrow(
            paymentId = paymentId,
            transactionId = transactionId
        )

        return when (updateResult) {
            is Resource.Success -> {
                val bookingId = updateResult.data.bookingId
                bookingRepository.confirmBooking(bookingId)
                runCatching { walletRepository.holdEscrow(updateResult.data.id) }
                Resource.Success(
                    Result.BookingPayment(
                        paymentId = updateResult.data.id,
                        bookingId = bookingId
                    )
                )
            }
            is Resource.Error -> Resource.Error(updateResult.message)
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
