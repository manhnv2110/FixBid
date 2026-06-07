package com.example.fixbid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.fixbid.domain.model.BidStatus
import com.example.fixbid.domain.model.BookingStatus

/**
 * Semantic status palette.
 *
 * These colours sit *outside* the Material [androidx.compose.material3.ColorScheme]
 * because they carry domain meaning (a booking's lifecycle, a bid's outcome) rather
 * than a UI role. Crucially they ship light **and** dark variants so status chips
 * keep correct contrast in both themes — previously the raw `StatusOrange`/`StatusGreen`
 * constants were fixed light-mode tones that washed out on a dark background.
 *
 * Access them through [LocalStatusColors] or the [statusColor] / [statusLabel]
 * helpers so a single source of truth drives every status indicator in the app.
 */
data class StatusColors(
    val pending: Color,            // chờ xác nhận / generic pending
    val confirmed: Color,          // đã xác nhận
    val inProgress: Color,         // đang thực hiện
    val pendingCompletion: Color,  // chờ khách xác nhận hoàn thành
    val completed: Color,          // hoàn thành
    val cancelled: Color,          // đã huỷ / trung tính
    val disputed: Color,           // tranh chấp
    val bidding: Color,            // đang nhận báo giá
    val quoted: Color,             // thợ vừa báo giá, chờ khách duyệt (direct)
    val awaitingPayment: Color,    // chờ thanh toán
    val rating: Color,             // sao đánh giá
    val positive: Color,           // tiền vào ví / thành công
    val negative: Color,           // hoàn tiền / thất bại
    val neutral: Color,            // đang giữ / chưa rõ
    val info: Color                // thông tin phụ
)

internal val LightStatusColors = StatusColors(
    pending = Color(0xFFFFA000),
    confirmed = Color(0xFF1565C0),
    inProgress = Color(0xFF2196F3),
    pendingCompletion = Color(0xFFE65100),
    completed = Color(0xFF43A047),
    cancelled = Color(0xFFB0BEC5),
    disputed = Color(0xFFD32F2F),
    bidding = Color(0xFF00897B),
    quoted = Color(0xFF7B1FA2),
    awaitingPayment = Color(0xFFF57C00),
    rating = Color(0xFFFFA726),
    positive = Color(0xFF43A047),
    negative = Color(0xFFD32F2F),
    neutral = Color(0xFFB0BEC5),
    info = Color(0xFF1565C0)
)

internal val DarkStatusColors = StatusColors(
    pending = Color(0xFFFFD54F),
    confirmed = Color(0xFF64B5F6),
    inProgress = Color(0xFF64B5F6),
    pendingCompletion = Color(0xFFFF8A65),
    completed = Color(0xFF81C784),
    cancelled = Color(0xFFCFD8DC),
    disputed = Color(0xFFE57373),
    bidding = Color(0xFF4DB6AC),
    quoted = Color(0xFFCE93D8),
    awaitingPayment = Color(0xFFFFB74D),
    rating = Color(0xFFFFD54F),
    positive = Color(0xFF81C784),
    negative = Color(0xFFE57373),
    neutral = Color(0xFFCFD8DC),
    info = Color(0xFF64B5F6)
)

/** Provided by [FixBidTheme]; falls back to the light palette outside the theme. */
val LocalStatusColors = compositionLocalOf { LightStatusColors }

/** Convenience accessor: `StatusColorsTheme.current.completed`. */
object StatusColorsTheme {
    val current: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}

// ─── Domain → colour / label mappers ─────────────────────────────────────────
// Single source of truth so every screen renders a status the same way.

@Composable
@ReadOnlyComposable
fun statusColor(status: BookingStatus): Color {
    val c = LocalStatusColors.current
    return when (status) {
        BookingStatus.PENDING -> c.pending
        BookingStatus.BIDDING -> c.bidding
        BookingStatus.QUOTED -> c.quoted
        BookingStatus.AWAITING_PAYMENT -> c.awaitingPayment
        BookingStatus.CONFIRMED -> c.confirmed
        BookingStatus.IN_PROGRESS -> c.inProgress
        BookingStatus.PENDING_COMPLETION -> c.pendingCompletion
        BookingStatus.COMPLETED -> c.completed
        BookingStatus.CANCELLED -> c.cancelled
        BookingStatus.DISPUTED -> c.disputed
    }
}

fun statusLabel(status: BookingStatus): String = when (status) {
    BookingStatus.PENDING -> "Chờ xác nhận"
    BookingStatus.BIDDING -> "Chờ báo giá"
    BookingStatus.QUOTED -> "Chờ duyệt báo giá"
    BookingStatus.AWAITING_PAYMENT -> "Chờ thanh toán"
    BookingStatus.CONFIRMED -> "Đã xác nhận"
    BookingStatus.IN_PROGRESS -> "Đang làm"
    BookingStatus.PENDING_COMPLETION -> "Chờ xác nhận hoàn thành"
    BookingStatus.COMPLETED -> "Hoàn thành"
    BookingStatus.CANCELLED -> "Đã huỷ"
    BookingStatus.DISPUTED -> "Tranh chấp"
}

@Composable
@ReadOnlyComposable
fun statusColor(status: BidStatus): Color {
    val c = LocalStatusColors.current
    return when (status) {
        BidStatus.PENDING -> c.pending
        BidStatus.ACCEPTED -> c.positive
        BidStatus.REJECTED -> c.negative
        BidStatus.WITHDRAWN -> c.neutral
    }
}
