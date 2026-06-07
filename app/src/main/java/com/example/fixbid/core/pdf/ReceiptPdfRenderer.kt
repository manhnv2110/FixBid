package com.example.fixbid.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.format.DateFormat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.domain.model.PaymentReceipt
import com.example.fixbid.domain.model.PaymentReceiptIssuer
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a [PaymentReceipt] into an A4 PDF using only Android's built-in
 * [PdfDocument] API — no third-party PDF library, keeps the APK lean.
 *
 * Layout follows Vietnamese receipt conventions (NĐ 123/2020 + TT 78/2021)
 * so the document is recognisable to anyone used to handling tax invoices:
 *
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │ Logo + "BIÊN LAI THU TIỀN — Service collection receipt"        │
 *   │ Mẫu số / Ký hiệu / Số biên lai / Ngày lập                      │
 *   ├────────────────────────────────────────────────────────────────┤
 *   │ ĐƠN VỊ PHÁT HÀNH    | KHÁCH HÀNG                                │
 *   │ Tên / MST / ĐC      | Tên / SĐT / ĐC                            │
 *   ├────────────────────────────────────────────────────────────────┤
 *   │ Bảng dịch vụ:                                                  │
 *   │  STT | Mô tả | ĐVT | SL | Đơn giá | Thành tiền                  │
 *   │  ...                                                            │
 *   ├────────────────────────────────────────────────────────────────┤
 *   │ Cộng tiền hàng | Thuế suất | Tiền thuế | Tổng cộng              │
 *   ├────────────────────────────────────────────────────────────────┤
 *   │ Số tiền viết bằng chữ                                           │
 *   │ Hình thức thanh toán                                            │
 *   ├────────────────────────────────────────────────────────────────┤
 *   │ Footer: QR verify | "Đây là biên lai điện tử"                   │
 *   └────────────────────────────────────────────────────────────────┘
 *
 * The output Uri is wrapped in [FileProvider] so other apps (Drive, email,
 * Zalo) can open it via the share sheet.
 */
@Singleton
class ReceiptPdfRenderer @Inject constructor() {

    /**
     * Render [receipt] into a PDF saved to the app's cache and return a
     * shareable content Uri.
     *
     * The cache file name encodes the serial so re-rendering the same
     * receipt overwrites the previous file (cache stays small).
     */
    fun render(context: Context, receipt: PaymentReceipt): android.net.Uri {
        val document = PdfDocument()
        try {
            // A4 at 72dpi → 595 × 842 pt. Keeping the standard size means the
            // PDF prints correctly on any system without a scale dialog.
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            drawReceipt(page.canvas, receipt, context)
            document.finishPage(page)

            val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val file = File(dir, "${sanitizeFileName(receipt.serial)}.pdf")
            FileOutputStream(file).use { document.writeTo(it) }

            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } finally {
            document.close()
        }
    }

    /**
     * For non-shared rendering (e.g. printing): write the PDF straight to a
     * caller-supplied File. Returns the file for chaining. Doesn't touch
     * FileProvider.
     */
    fun renderToFile(receipt: PaymentReceipt, target: File): File {
        target.parentFile?.mkdirs()
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            drawReceipt(page.canvas, receipt, null)
            document.finishPage(page)
            FileOutputStream(target).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return target
    }

    // ── Layout ──────────────────────────────────────────────────────────────

    private fun drawReceipt(canvas: Canvas, receipt: PaymentReceipt, context: Context?) {
        // Margins are intentionally generous — VN tax-office samples leave
        // ~24pt of breathing room and the PDF prints crisper that way.
        val left = MARGIN.toFloat()
        val right = (A4_WIDTH - MARGIN).toFloat()
        var cursorY = MARGIN.toFloat()

        // ── Header band ─────────────────────────────────────────────────────
        canvas.drawRect(
            RectF(0f, 0f, A4_WIDTH.toFloat(), HEADER_HEIGHT.toFloat()),
            paintFill(BRAND_PRIMARY)
        )

        // Brand mark (no asset needed — sparkle "★" suggests the FixBid logo
        // without forcing a PNG into the APK).
        val brandPaint = textPaint(28f, Color.WHITE, bold = true)
        canvas.drawText("FixBid", left, MARGIN + 24f, brandPaint)

        val taglinePaint = textPaint(11f, Color.WHITE.alpha(0.85f))
        canvas.drawText(
            "Nền tảng đặt thợ dịch vụ",
            left,
            MARGIN + 42f,
            taglinePaint
        )

        // Right-aligned receipt meta
        val metaPaint = textPaint(10f, Color.WHITE)
        val metaRows = listOf(
            "Mẫu số: 01/BL-FB",
            "Ký hiệu: FB${yearOf(receipt.issuedAt) % 100}/E",
            "Số: ${receipt.serial}",
            "Ngày: ${formatDate(receipt.issuedAt)}"
        )
        metaRows.forEachIndexed { i, line ->
            val w = metaPaint.measureText(line)
            canvas.drawText(line, right - w, MARGIN + 14f + i * 12f, metaPaint)
        }

        cursorY = HEADER_HEIGHT + 24f

        // ── Title ───────────────────────────────────────────────────────────
        val titlePaint = textPaint(20f, BRAND_PRIMARY, bold = true)
        val title = "BIÊN LAI THU TIỀN DỊCH VỤ"
        val titleW = titlePaint.measureText(title)
        canvas.drawText(title, (A4_WIDTH - titleW) / 2f, cursorY, titlePaint)

        cursorY += 16f
        val subtitlePaint = textPaint(10f, MUTED).apply { isFakeBoldText = false }
        val subtitle = "Service collection receipt — Lưu hành nội bộ"
        val subW = subtitlePaint.measureText(subtitle)
        canvas.drawText(subtitle, (A4_WIDTH - subW) / 2f, cursorY, subtitlePaint)

        cursorY += 24f

        // ── Two-column "from / to" block ────────────────────────────────────
        val colWidth = (right - left - 16f) / 2f
        val colTop = cursorY

        cursorY = drawPartyBlock(
            canvas = canvas,
            x = left,
            yStart = colTop,
            width = colWidth,
            heading = "ĐƠN VỊ PHÁT HÀNH",
            lines = sellerLines(receipt.seller)
        )

        val rightCursorEnd = drawPartyBlock(
            canvas = canvas,
            x = left + colWidth + 16f,
            yStart = colTop,
            width = colWidth,
            heading = "KHÁCH HÀNG",
            lines = buyerLines(receipt)
        )

        cursorY = max(cursorY, rightCursorEnd) + 14f

        // ── Service table header ────────────────────────────────────────────
        val tableTop = cursorY
        canvas.drawRect(
            RectF(left, tableTop, right, tableTop + 22f),
            paintFill(BRAND_PRIMARY_SOFT)
        )
        val cols = computeColumnXs(left, right)
        val headerPaint = textPaint(10f, Color.BLACK, bold = true)
        val headerLabels = listOf("STT", "Mô tả dịch vụ", "ĐVT", "SL", "Đơn giá", "Thành tiền")
        cols.forEachIndexed { i, cx ->
            val text = headerLabels[i]
            val align = if (i == 0 || i == 2 || i == 3) cx else
                if (i == 4 || i == 5) cx - headerPaint.measureText(text) else cx
            canvas.drawText(text, align, tableTop + 15f, headerPaint)
        }
        cursorY = tableTop + 22f

        // ── Service line ────────────────────────────────────────────────────
        // We model the booking as a single line item ("đơn vị tính: lần"). When
        // FixBid grows multi-line bookings (e.g. recurring weekly cleans
        // batched into one receipt), this section becomes a loop.
        val rowTop = cursorY
        val rowPaint = textPaint(10f, Color.BLACK)
        val description = listOfNotNull(
            "[${receipt.serviceCategory}]",
            receipt.serviceDescription.takeIf { it.isNotBlank() },
            "Thợ thực hiện: ${receipt.workerName}".takeIf { receipt.workerName.isNotBlank() }
        ).joinToString("\n")
        val descLines = wrapText(description, rowPaint, cols[2] - cols[1] - 8f)
        val rowHeight = max(28f, descLines.size * 12f + 12f)

        // STT
        canvas.drawText("1", cols[0], rowTop + 14f, rowPaint)
        // Description (may wrap)
        descLines.forEachIndexed { i, line ->
            canvas.drawText(line, cols[1], rowTop + 14f + i * 12f, rowPaint)
        }
        // ĐVT, SL
        canvas.drawText("Lần", cols[2], rowTop + 14f, rowPaint)
        canvas.drawText("1", cols[3], rowTop + 14f, rowPaint)
        // Đơn giá / Thành tiền (right aligned)
        val priceText = formatCurrencyVnd(receipt.amount)
        canvas.drawText(
            priceText,
            cols[4] - rowPaint.measureText(priceText),
            rowTop + 14f,
            rowPaint
        )
        canvas.drawText(
            priceText,
            cols[5] - rowPaint.measureText(priceText),
            rowTop + 14f,
            rowPaint
        )
        cursorY = rowTop + rowHeight

        // Bottom border for the table
        canvas.drawLine(left, cursorY, right, cursorY, paintStroke(BORDER, 0.6f))

        cursorY += 18f

        // ── Totals ──────────────────────────────────────────────────────────
        val totalsLeft = right - 240f
        val totalsRight = right
        val totalsLineHeight = 18f
        val totalRows = listOf(
            "Cộng tiền hàng" to receipt.amount,
            "Phí nền tảng" to -receipt.platformFee,
            "Thợ thực nhận" to receipt.workerReceives,
            "Thuế GTGT (${formatVatRate(receipt.vatRate)}%)" to receipt.vatAmount
        )
        val totalsPaint = textPaint(10f, Color.BLACK)
        totalRows.forEach { (label, value) ->
            val labelText = label
            val valueText = formatCurrencyVnd(value)
            canvas.drawText(labelText, totalsLeft, cursorY, totalsPaint)
            canvas.drawText(
                valueText,
                totalsRight - totalsPaint.measureText(valueText),
                cursorY,
                totalsPaint
            )
            cursorY += totalsLineHeight
        }
        // Grand total — bold and slightly bigger.
        canvas.drawLine(
            totalsLeft,
            cursorY - 6f,
            totalsRight,
            cursorY - 6f,
            paintStroke(BORDER, 0.8f)
        )
        val grandTotalPaint = textPaint(13f, BRAND_PRIMARY, bold = true)
        val grandTotalLabel = "TỔNG TIỀN THANH TOÁN"
        val grandTotalValue = formatCurrencyVnd(receipt.amount + receipt.vatAmount)
        canvas.drawText(grandTotalLabel, totalsLeft, cursorY + 8f, grandTotalPaint)
        canvas.drawText(
            grandTotalValue,
            totalsRight - grandTotalPaint.measureText(grandTotalValue),
            cursorY + 8f,
            grandTotalPaint
        )
        cursorY += 22f

        // ── Number-in-words ─────────────────────────────────────────────────
        cursorY += 12f
        val wordsPaint = textPaint(10f, Color.BLACK).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC) }
        val totalAmountWords =
            "Bằng chữ: ${capitalizeFirst(numberToVietnameseWords((receipt.amount + receipt.vatAmount).toLong()))} đồng."
        wrapText(totalAmountWords, wordsPaint, right - left).forEach { line ->
            canvas.drawText(line, left, cursorY, wordsPaint)
            cursorY += 14f
        }

        // ── Payment method + transaction ────────────────────────────────────
        cursorY += 6f
        val metaSubPaint = textPaint(10f, Color.BLACK)
        canvas.drawText(
            "Hình thức thanh toán: ${receipt.paymentMethodLabel}",
            left,
            cursorY,
            metaSubPaint
        )
        receipt.transactionId?.takeIf { it.isNotBlank() }?.let {
            cursorY += 14f
            canvas.drawText("Mã giao dịch: $it", left, cursorY, metaSubPaint)
        }
        cursorY += 14f
        canvas.drawText(
            "Mã đơn dịch vụ: #${receipt.bookingId.take(12).uppercase()}",
            left,
            cursorY,
            metaSubPaint
        )

        // ── Footer ──────────────────────────────────────────────────────────
        val footerY = (A4_HEIGHT - 80).toFloat()
        canvas.drawLine(left, footerY, right, footerY, paintStroke(BORDER, 0.4f))

        val notePaint = textPaint(9f, MUTED)
        val noteLines = listOf(
            "Đây là biên lai thu tiền điện tử do FixBid phát hành cho dịch vụ trung gian.",
            "Biên lai có giá trị đối chiếu giao dịch, chưa phải hoá đơn GTGT (VAT) chính thức.",
            "Khách hàng cần hóa đơn VAT vui lòng yêu cầu trực tiếp với người cung cấp dịch vụ (nếu có MST)."
        )
        noteLines.forEachIndexed { i, line ->
            canvas.drawText(line, left, footerY + 14f + i * 12f, notePaint)
        }

        // QR placeholder — only render if we have a verify URL
        receipt.verifyUrl?.let { drawQrPlaceholder(canvas, right - 70f, footerY + 8f, 60f) }
    }

    /**
     * Renders one of the two "Người bán / Người mua" panels.
     * Returns the y after the panel so the parent can stack content.
     */
    private fun drawPartyBlock(
        canvas: Canvas,
        x: Float,
        yStart: Float,
        width: Float,
        heading: String,
        lines: List<Pair<String, String>>
    ): Float {
        val top = yStart
        val padding = 10f

        // Compute height first so we can draw the border in one shot.
        val labelPaint = textPaint(9f, MUTED, bold = true)
        val valuePaint = textPaint(11f, Color.BLACK)
        var contentH = 24f // heading row
        lines.forEach { (_, value) ->
            val wrapped = wrapText(value, valuePaint, width - padding * 2)
            contentH += 12f + wrapped.size * 14f
        }
        contentH += padding

        canvas.drawRect(
            RectF(x, top, x + width, top + contentH),
            paintStroke(BORDER, 0.6f)
        )

        // Heading band
        canvas.drawRect(
            RectF(x, top, x + width, top + 22f),
            paintFill(BRAND_PRIMARY_SOFT)
        )
        val headingPaint = textPaint(10f, BRAND_PRIMARY, bold = true)
        canvas.drawText(heading, x + padding, top + 15f, headingPaint)

        var y = top + 22f + padding
        lines.forEach { (label, value) ->
            canvas.drawText(label, x + padding, y, labelPaint)
            y += 12f
            wrapText(value, valuePaint, width - padding * 2).forEach { line ->
                canvas.drawText(line, x + padding, y, valuePaint)
                y += 14f
            }
        }
        return top + contentH
    }

    /**
     * Stub QR — we don't pull in zxing for a 60x60 graphic. We draw a
     * crosshatch placeholder that reads as "scan code" without claiming
     * the data is actually encoded. When zxing/coil-zxing lands in the
     * project, swap this for a real bitmap.
     */
    private fun drawQrPlaceholder(canvas: Canvas, x: Float, y: Float, size: Float) {
        canvas.drawRect(RectF(x, y, x + size, y + size), paintStroke(Color.BLACK, 1f))
        val cell = size / 6f
        for (i in 0 until 6) {
            for (j in 0 until 6) {
                if ((i + j) % 2 == 0 && (i in 1..4 || j in 1..4)) {
                    canvas.drawRect(
                        RectF(x + i * cell, y + j * cell, x + (i + 1) * cell, y + (j + 1) * cell),
                        paintFill(Color.BLACK)
                    )
                }
            }
        }
        // Three locator squares (top-left, top-right, bottom-left) to look
        // like a real QR code.
        listOf(0f to 0f, size - cell * 2 to 0f, 0f to size - cell * 2).forEach { (dx, dy) ->
            canvas.drawRect(
                RectF(x + dx, y + dy, x + dx + cell * 2, y + dy + cell * 2),
                paintStroke(Color.BLACK, 1.4f)
            )
        }
    }

    // ── Field formatting helpers ────────────────────────────────────────────

    private fun sellerLines(seller: PaymentReceiptIssuer): List<Pair<String, String>> = buildList {
        add("Đơn vị" to seller.displayName)
        seller.taxId?.takeIf { it.isNotBlank() }?.let { add("Mã số thuế" to it) }
        add("Địa chỉ" to seller.address)
        seller.phone?.takeIf { it.isNotBlank() }?.let { add("Điện thoại" to it) }
        seller.email?.takeIf { it.isNotBlank() }?.let { add("Email" to it) }
    }

    private fun buyerLines(receipt: PaymentReceipt): List<Pair<String, String>> = buildList {
        add("Họ và tên" to receipt.buyerName)
        receipt.buyerPhone?.takeIf { it.isNotBlank() }?.let { add("Điện thoại" to it) }
        add("Địa chỉ" to receipt.buyerAddress)
    }

    private fun formatVatRate(rate: Double): String =
        if (rate <= 0.0) "0" else String.format(Locale.US, "%.0f", rate * 100)

    // ── Drawing primitives ──────────────────────────────────────────────────

    private fun textPaint(sizePt: Float, color: Int, bold: Boolean = false) = TextPaint().apply {
        isAntiAlias = true
        textSize = sizePt
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun paintFill(color: Int) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color
    }

    private fun paintStroke(color: Int, width: Float) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

    /**
     * Single-line word wrap — splits [text] on whitespace and concatenates
     * tokens until the next one would overflow [maxWidth]. Newline characters
     * in the source are honoured so multi-line descriptions render correctly.
     */
    private fun wrapText(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) {
                out.add("")
                return@forEach
            }
            val words = paragraph.split(' ')
            val current = StringBuilder()
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else current.toString() + " " + word
                if (paint.measureText(candidate) <= maxWidth) {
                    current.clear()
                    current.append(candidate)
                } else {
                    if (current.isNotEmpty()) out.add(current.toString())
                    current.clear()
                    current.append(word)
                }
            }
            if (current.isNotEmpty()) out.add(current.toString())
        }
        return out
    }

    private fun computeColumnXs(left: Float, right: Float): List<Float> {
        val widths = floatArrayOf(0.06f, 0.44f, 0.08f, 0.08f, 0.17f, 0.17f)
        val total = right - left
        val xs = mutableListOf<Float>()
        var x = left + 8f
        for (i in widths.indices) {
            xs.add(x)
            x += widths[i] * total
        }
        // Right-edge of the last 2 columns (we right-align numbers there).
        val edited = xs.toMutableList()
        edited[4] = left + (widths[0] + widths[1] + widths[2] + widths[3] + widths[4]) * total + 0f
        edited[5] = right - 8f
        return edited
    }

    private fun yearOf(epochMs: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        return cal.get(java.util.Calendar.YEAR)
    }

    private fun formatDate(epochMs: Long): String =
        DateFormat.format("dd/MM/yyyy", Date(epochMs)).toString()

    private fun sanitizeFileName(serial: String): String =
        "FixBid_BienLai_${serial.replace('/', '_').replace(' ', '_')}"

    private fun capitalizeFirst(s: String): String =
        if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)

    private fun Int.alpha(alpha: Float): Int {
        val a = (Color.alpha(this) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(this), Color.green(this), Color.blue(this))
    }

    private companion object {
        const val A4_WIDTH = 595
        const val A4_HEIGHT = 842
        const val MARGIN = 36
        const val HEADER_HEIGHT = 78

        const val BRAND_PRIMARY = 0xFF00629E.toInt() // matches md_theme_light_primary
        const val BRAND_PRIMARY_SOFT = 0xFFCFE5FF.toInt()
        const val BORDER = 0xFF666666.toInt()
        const val MUTED = 0xFF555555.toInt()
    }
}

/**
 * Convert an integer VND amount to Vietnamese words for the "Bằng chữ" line
 * required on every Vietnamese receipt. Handles 0 → 999_999_999_999 — the
 * upper bound is safely past anything FixBid will ever invoice.
 *
 * Implementation note: we walk groups of three digits from the most-significant
 * end and append the corresponding magnitude word (tỷ, triệu, nghìn). The
 * "lẻ", "linh", "mươi" handling matches mainstream Vietnamese tax-invoice
 * conventions used by major banks (BIDV, Vietcombank).
 */
internal fun numberToVietnameseWords(amount: Long): String {
    if (amount == 0L) return "không"
    if (amount < 0L) return "âm " + numberToVietnameseWords(-amount)

    val units = arrayOf("", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín")
    val magnitudes = arrayOf("", "nghìn", "triệu", "tỷ")

    fun threeDigits(n: Int, isLeadingGroup: Boolean): String {
        val hundreds = n / 100
        val tens = (n % 100) / 10
        val ones = n % 10
        val sb = StringBuilder()
        if (hundreds > 0) {
            sb.append(units[hundreds]).append(" trăm")
        } else if (!isLeadingGroup && (tens > 0 || ones > 0)) {
            sb.append("không trăm")
        }
        if (tens > 0) {
            if (sb.isNotEmpty()) sb.append(' ')
            when (tens) {
                1 -> sb.append("mười")
                else -> sb.append(units[tens]).append(" mươi")
            }
        } else if (ones > 0 && (hundreds > 0 || !isLeadingGroup)) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append("lẻ")
        }
        if (ones > 0) {
            if (sb.isNotEmpty()) sb.append(' ')
            when {
                tens > 1 && ones == 1 -> sb.append("mốt")
                tens > 0 && ones == 5 -> sb.append("lăm")
                else -> sb.append(units[ones])
            }
        }
        return sb.toString()
    }

    // Split into 3-digit groups from least significant.
    val groups = mutableListOf<Int>()
    var n = amount
    while (n > 0) {
        groups.add((n % 1000).toInt())
        n /= 1000
    }
    val parts = mutableListOf<String>()
    for (i in groups.indices.reversed()) {
        val g = groups[i]
        if (g == 0) continue
        val isLeading = (parts.isEmpty())
        val text = threeDigits(g, isLeading)
        val mag = magnitudes[i]
        parts.add(if (mag.isBlank()) text else "$text $mag")
    }
    return parts.joinToString(" ").trim()
}
