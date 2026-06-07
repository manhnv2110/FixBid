package com.example.fixbid.domain.model

/**
 * Snapshot of every field that goes onto a payment receipt PDF.
 *
 * Why a dedicated type vs reusing [Payment] + [Booking]?
 *  - Receipts are LEGAL documents. The PDF must capture the values *as
 *    they were the day the receipt was issued* — even if the underlying
 *    booking/payment row is later edited or deleted, the issued PDF must
 *    remain valid. Snapshotting into a value object makes that intent
 *    explicit and lets us add tax/serial fields the underlying tables
 *    don't have.
 *  - Vietnam invoice law (NĐ123/2020 + TT78/2021 + recent NĐ70/2025
 *    update for 2025-2026) requires specific fields: số biên lai (mẫu
 *    + serial), ngày lập, người mua / người bán with MST or CCCD, mô tả
 *    hàng hoá / dịch vụ, đơn vị tính, số lượng, đơn giá, thành tiền,
 *    thuế suất, tiền thuế, tổng tiền thanh toán, hình thức thanh toán.
 *    The fields below cover that contract.
 *
 * Note on positioning: FixBid acts as an intermediary platform — the
 * platform is not (yet) a registered VAT-issuer for the entire transaction.
 * This receipt is a "BIÊN LAI THU TIỀN / PHIẾU THU DỊCH VỤ" (collection
 * receipt) the customer can use to claim personal expenses or request a
 * proper VAT e-invoice from the worker if they're VAT-registered. The
 * structure mirrors a Vietnamese tax invoice so we can switch to issuing
 * official e-invoices later (with TCT-issued mã CQT) without changing UI.
 */
data class PaymentReceipt(
    /** Internal serial — `BL/2026/00001234` format. Stable per (year, paymentId). */
    val serial: String,

    /** Receipt issue date — usually `payment.paidAt` (epoch millis, UTC). */
    val issuedAt: Long,

    /** Booking id — included for cross-reference, prefixed `#` in render. */
    val bookingId: String,
    val paymentId: String,
    val transactionId: String?,

    // ── Buyer (khách hàng) ───────────────────────────────────────────────────
    val buyerName: String,
    val buyerPhone: String?,
    val buyerAddress: String,

    // ── Seller (FixBid platform OR the worker, see PaymentReceiptIssuer) ─────
    val seller: PaymentReceiptIssuer,

    // ── Service line items ───────────────────────────────────────────────────
    /** Plain-text description of the work performed. */
    val serviceCategory: String,
    val serviceDescription: String,
    /** Worker who actually performed the work (for transparency). */
    val workerName: String,
    val workerPhone: String?,

    // ── Money ────────────────────────────────────────────────────────────────
    val amount: Double,           // tổng tiền khách trả
    val platformFee: Double,      // phí app FixBid giữ lại
    val workerReceives: Double,   // amount - platformFee (tiền thợ thực nhận)
    val vatRate: Double = 0.0,    // thuế suất % — 0 cho biên lai (chưa phải hoá đơn VAT)
    val vatAmount: Double = 0.0,  // tiền thuế tương ứng
    val paymentMethodLabel: String,

    // ── Verification ─────────────────────────────────────────────────────────
    /** Public URL the QR code points to so receivers can verify authenticity. */
    val verifyUrl: String? = null
)

/**
 * Identity block printed in the "Người bán / Đơn vị phát hành" section.
 *
 * Two variants:
 *  - [Platform]: FixBid as a platform issuing a service-collection receipt
 *    on behalf of the worker. Used by default — the buyer's transactional
 *    counterparty in the app is FixBid (escrow holder).
 *  - [Worker]: when the worker is a registered HKD/DN with MST. The PDF
 *    can switch the seller block to the worker's legal name + tax code so
 *    the customer can use it for company expense claims. Surfaced once
 *    the worker has filled out tax info in their profile (future work).
 */
sealed interface PaymentReceiptIssuer {
    val displayName: String
    val taxId: String?       // Mã số thuế (MST) — null for individuals
    val address: String
    val phone: String?
    val email: String?

    data class Platform(
        override val displayName: String = "FixBid Platform",
        override val taxId: String? = null,
        override val address: String = "Hà Nội, Việt Nam",
        override val phone: String? = null,
        override val email: String? = "support@fixbid.vn"
    ) : PaymentReceiptIssuer

    data class Worker(
        override val displayName: String,
        override val taxId: String?,
        override val address: String,
        override val phone: String?,
        override val email: String? = null
    ) : PaymentReceiptIssuer
}
