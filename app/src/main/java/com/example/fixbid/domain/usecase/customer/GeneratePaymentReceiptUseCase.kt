package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentMethod
import com.example.fixbid.domain.model.PaymentReceipt
import com.example.fixbid.domain.model.PaymentReceiptIssuer
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import javax.inject.Inject

/**
 * Pulls everything the receipt PDF needs from the data layer and snapshots
 * it into a [PaymentReceipt] value object. Called from
 * `CustomerBookingDetailViewModel.downloadReceipt()` (and the chatbot's
 * future `download_receipt` tool).
 *
 * Why a UseCase instead of letting the VM compose: the receipt is a
 * legal-ish document. We want a single place that:
 *   - validates the payment is in a state where a receipt is meaningful
 *     (escrow released, paidAt set);
 *   - assembles the seller block from the right source (platform vs
 *     worker, depending on tax registration — future);
 *   - builds the deterministic [serial] so re-generating the same receipt
 *     produces the same file name.
 */
class GeneratePaymentReceiptUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val authRepository: AuthRepository
) {

    suspend operator fun invoke(bookingId: String): Resource<PaymentReceipt> {
        val booking = (bookingRepository.getBookingById(bookingId) as? Resource.Success)?.data
            ?: return Resource.Error("Không tìm thấy đơn dịch vụ")

        val payment = (paymentRepository.getPaymentByBooking(bookingId) as? Resource.Success)?.data
            ?: return Resource.Error("Đơn này chưa có giao dịch thanh toán")

        // Only completed (escrow released or method=cash with paidAt set)
        // payments are eligible. Otherwise the receipt could legitimise a
        // payment that hasn't actually settled.
        if (payment.paidAt == null && payment.releasedAt == null) {
            return Resource.Error("Giao dịch chưa hoàn tất, chưa thể xuất biên lai")
        }

        val customer = booking.customer ?: authRepository.getCurrentUser()
        val workerName = booking.worker?.fullName ?: "Thợ dịch vụ FixBid"
        val workerPhone = booking.worker?.phoneNumber

        val (buyerName, buyerPhone) = parseCustomerNote(booking.customerNote)
            ?: ((customer?.fullName ?: "Khách hàng") to customer?.phoneNumber)

        val receipt = PaymentReceipt(
            serial = buildSerial(payment),
            issuedAt = payment.paidAt ?: payment.releasedAt ?: payment.createdAt,
            bookingId = booking.id,
            paymentId = payment.id,
            transactionId = payment.transactionId,
            buyerName = buyerName,
            buyerPhone = buyerPhone,
            buyerAddress = booking.address,
            seller = PaymentReceiptIssuer.Platform(),
            serviceCategory = booking.category.displayName,
            serviceDescription = booking.description,
            workerName = workerName,
            workerPhone = workerPhone,
            amount = payment.amount,
            platformFee = payment.platformFee,
            workerReceives = payment.workerReceives,
            // Biên lai (collection receipt) — không phải HĐ VAT, nên thuế
            // suất 0. Khi platform onboard E-invoice provider TCT, đổi thành
            // 8 hoặc 10 và tính lại total.
            vatRate = 0.0,
            vatAmount = 0.0,
            paymentMethodLabel = formatPaymentMethod(payment.method),
            verifyUrl = "https://fixbid.vn/r/${payment.id}"
        )
        return Resource.Success(receipt)
    }

    /**
     * Receipt serial: `BL/2026/00012345`. Stable per (year, paymentId) so
     * regenerating the same receipt yields the same number — important if
     * the customer downloads it twice and forwards both copies to a tax
     * accountant who expects identical documents.
     *
     * The number comes from the first 8 hex chars of the paymentId UUID so
     * we don't need a server-side counter (which would otherwise force a
     * round-trip and complicate offline rendering).
     */
    private fun buildSerial(payment: Payment): String {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = payment.paidAt ?: payment.releasedAt ?: payment.createdAt
        }
        val year = cal.get(java.util.Calendar.YEAR)
        // First 8 hex chars of UUID → 32-bit unsigned → 0..4_294_967_295.
        // We mod by 100_000_000 so the visible number stays a tidy 8 digits.
        val numericPart = payment.id
            .replace("-", "")
            .take(8)
            .toLongOrNull(16)
            ?.let { it % 100_000_000 }
            ?.toString()
            ?.padStart(8, '0')
            ?: payment.id.takeLast(8).uppercase()
        return "BL/$year/$numericPart"
    }

    private fun formatPaymentMethod(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "Tiền mặt"
        PaymentMethod.MOMO -> "Ví MoMo"
        PaymentMethod.VNPAY -> "VNPay (chuyển khoản qua cổng)"
        PaymentMethod.BANK_TRANSFER -> "Chuyển khoản ngân hàng"
    }

    /**
     * Customer notes follow the format set in BookingScreen:
     *   "SĐT: 0901xxxxxxx\nTên: Nguyễn Văn A\nGhi chú: ..."
     * Parsing them gives us the most accurate buyer info — better than the
     * authenticated user's profile because the customer might book on
     * behalf of a relative.
     */
    private fun parseCustomerNote(note: String?): Pair<String, String?>? {
        if (note.isNullOrBlank()) return null
        val lines = note.lines()
        val name = lines.find { it.startsWith("Tên: ") }?.substringAfter("Tên: ")?.trim()
        val phone = lines.find { it.startsWith("SĐT: ") }?.substringAfter("SĐT: ")?.trim()
        if (name.isNullOrBlank()) return null
        return name to phone
    }
}
