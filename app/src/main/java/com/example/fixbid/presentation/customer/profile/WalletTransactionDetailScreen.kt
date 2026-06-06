package com.example.fixbid.presentation.customer.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.toFormattedDate
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.EscrowStatus
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentStatus
import com.example.fixbid.domain.model.WalletTopup
import com.example.fixbid.domain.model.WalletTopupStatus
import com.example.fixbid.domain.model.WalletTransaction
import com.example.fixbid.domain.model.WalletTransactionType
import com.example.fixbid.domain.model.WalletWithdrawal
import com.example.fixbid.domain.model.WalletWithdrawalStatus
import com.example.fixbid.ui.theme.AccentGreen
import com.example.fixbid.ui.theme.StatusColorsTheme

/**
 * Drill-down view for a single wallet ledger row. Renders a hero block (icon,
 * signed amount, type, timestamp) and a type-specific information card with
 * everything we know about the transaction — booking + worker for escrow rows,
 * VNPay status for top-ups, bank account info + processing status for
 * withdrawals.
 *
 * Tapping a CTA on the related-entity card (Booking, Wallet) routes back to
 * the appropriate screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletTransactionDetailScreen(
    onBackClick: () -> Unit,
    onBookingClick: (String) -> Unit = {},
    viewModel: WalletTransactionDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppHeader(title = "Chi tiết giao dịch", onBackClick = onBackClick) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.transaction == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                uiState.transaction == null && uiState.errorMessage != null -> {
                    ErrorState(
                        message = uiState.errorMessage!!,
                        onRetry = viewModel::load
                    )
                }
                uiState.transaction != null -> {
                    DetailContent(
                        state = uiState,
                        onBookingClick = onBookingClick
                    )
                }
            }
        }
    }
}

// ─── Content ─────────────────────────────────────────────────────────────────

@Composable
private fun DetailContent(
    state: WalletTransactionDetailUiState,
    onBookingClick: (String) -> Unit
) {
    val tx = state.transaction!!
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.add(
            WindowInsets(left = 16.dp, top = 16.dp, right = 16.dp, bottom = 16.dp)
        ).asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroCard(tx = tx) }
        item { LedgerSnapshotCard(tx = tx) }

        when (tx.type) {
            WalletTransactionType.ESCROW_HOLD,
            WalletTransactionType.ESCROW_RELEASE,
            WalletTransactionType.ESCROW_REFUND -> {
                state.booking?.let { booking ->
                    item {
                        BookingCard(
                            booking = booking,
                            payment = state.payment,
                            onClick = { onBookingClick(booking.id) }
                        )
                    }
                }
            }
            WalletTransactionType.TOPUP -> {
                state.topup?.let { item { TopupCard(topup = it) } }
            }
            WalletTransactionType.WITHDRAWAL_REQUEST,
            WalletTransactionType.WITHDRAWAL -> {
                state.withdrawal?.let { item { WithdrawalCard(withdrawal = it) } }
            }
            WalletTransactionType.ADJUSTMENT -> Unit
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Hero block ──────────────────────────────────────────────────────────────

private data class HeroSpec(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val direction: HeroDirection
)

private enum class HeroDirection { POSITIVE, NEGATIVE, NEUTRAL }

@Composable
private fun heroSpecFor(tx: WalletTransaction): HeroSpec {
    val isOwnRow = true // detail screen only shows the signed-in user's rows
    return when (tx.type) {
        WalletTransactionType.ESCROW_REFUND -> HeroSpec(
            "Hoàn tiền từ thợ", Icons.Outlined.SwapHoriz, AccentGreen, HeroDirection.POSITIVE
        )
        WalletTransactionType.TOPUP -> HeroSpec(
            "Nạp tiền vào ví", Icons.Outlined.ArrowDownward, AccentGreen, HeroDirection.POSITIVE
        )
        WalletTransactionType.ESCROW_HOLD -> HeroSpec(
            "Đang giữ", Icons.Outlined.HourglassEmpty,
            StatusColorsTheme.current.awaitingPayment, HeroDirection.NEUTRAL
        )
        WalletTransactionType.ESCROW_RELEASE -> HeroSpec(
            "Đã giải ngân cho thợ", Icons.Outlined.ArrowUpward,
            StatusColorsTheme.current.neutral, HeroDirection.NEUTRAL
        )
        WalletTransactionType.WITHDRAWAL_REQUEST -> HeroSpec(
            "Yêu cầu rút tiền", Icons.Outlined.HourglassEmpty,
            StatusColorsTheme.current.awaitingPayment,
            if (isOwnRow) HeroDirection.NEGATIVE else HeroDirection.NEUTRAL
        )
        WalletTransactionType.WITHDRAWAL -> HeroSpec(
            "Rút tiền thành công", Icons.Outlined.ArrowUpward,
            MaterialTheme.colorScheme.primary, HeroDirection.NEGATIVE
        )
        WalletTransactionType.ADJUSTMENT -> HeroSpec(
            "Điều chỉnh số dư", Icons.Outlined.SwapHoriz,
            MaterialTheme.colorScheme.onSurfaceVariant, HeroDirection.NEUTRAL
        )
    }
}

@Composable
private fun HeroCard(tx: WalletTransaction) {
    val spec = heroSpecFor(tx)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(spec.tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(spec.icon, contentDescription = null, tint = spec.tint, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = spec.label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatSigned(spec.direction, tx.amount),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = spec.tint
            )
            Spacer(Modifier.height(4.dp))
            if (tx.createdAt > 0L) {
                Text(
                    text = tx.createdAt.toFormattedDate("EEEE, dd/MM/yyyy 'lúc' HH:mm"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            tx.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = desc,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

private fun formatSigned(direction: HeroDirection, amount: Double): String {
    val formatted = formatCurrencyVnd(amount)
    return when (direction) {
        HeroDirection.POSITIVE -> "+$formatted"
        HeroDirection.NEGATIVE -> "-$formatted"
        HeroDirection.NEUTRAL -> formatted
    }
}

// ─── Ledger snapshot ─────────────────────────────────────────────────────────

@Composable
private fun LedgerSnapshotCard(tx: WalletTransaction) {
    DetailCard(title = "Sổ giao dịch") {
        InfoRow(label = "Mã giao dịch", value = tx.id.take(8).uppercase(), copyable = true, copyValue = tx.id)
        Divider()
        InfoRow(label = "Số dư khả dụng sau giao dịch", value = formatCurrencyVnd(tx.balanceAfter))
        if (tx.pendingBalanceAfter > 0.0) {
            Divider()
            InfoRow(
                label = "Đang tạm giữ sau giao dịch",
                value = formatCurrencyVnd(tx.pendingBalanceAfter)
            )
        }
        tx.reference?.takeIf { it.isNotBlank() }?.let {
            Divider()
            InfoRow(label = "Mã tham chiếu", value = it, copyable = true)
        }
    }
}

// ─── Booking card (escrow rows) ──────────────────────────────────────────────

@Composable
private fun BookingCard(
    booking: Booking,
    payment: Payment?,
    onClick: () -> Unit
) {
    DetailCard(
        title = "Đơn dịch vụ liên quan",
        action = {
            OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
                Text("Xem đơn", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    ) {
        InfoRow(
            label = "Dịch vụ",
            value = booking.category.displayName,
            icon = Icons.Outlined.Business
        )
        Divider()
        InfoRow(
            label = "Địa chỉ",
            value = booking.address,
            icon = Icons.Outlined.LocationOn
        )
        booking.worker?.let { worker ->
            Divider()
            InfoRow(
                label = "Thợ",
                value = worker.fullName,
                icon = Icons.Outlined.Person
            )
        }
        if (!booking.cancelReason.isNullOrBlank()) {
            Divider()
            InfoRow(
                label = "Lý do hủy",
                value = booking.cancelReason!!,
                emphasizeError = true
            )
        }
        payment?.let { p ->
            Divider()
            InfoRow(
                label = "Số tiền thanh toán",
                value = formatCurrencyVnd(p.amount)
            )
            Divider()
            InfoRow(
                label = "Trạng thái thanh toán",
                value = paymentStatusLabel(p)
            )
            if (p.transactionId?.isNotBlank() == true) {
                Divider()
                InfoRow(
                    label = "Mã VNPay",
                    value = p.transactionId!!,
                    copyable = true
                )
            }
        }
    }
}

private fun paymentStatusLabel(p: Payment): String {
    val statusVi = when (p.status) {
        PaymentStatus.PENDING -> "Chờ thanh toán"
        PaymentStatus.PROCESSING -> "Đang xử lý"
        PaymentStatus.ESCROW -> "Đang giữ trong ví"
        PaymentStatus.COMPLETED -> "Đã thanh toán"
        PaymentStatus.FAILED -> "Thất bại"
        PaymentStatus.REFUNDED -> "Đã hoàn tiền"
    }
    val escrowVi = when (p.escrowStatus) {
        EscrowStatus.HOLDING -> " · Đang giữ"
        EscrowStatus.RELEASED -> " · Đã giải ngân"
        EscrowStatus.REFUNDED -> " · Đã hoàn"
        EscrowStatus.NONE -> ""
    }
    return statusVi + escrowVi
}

// ─── Top-up card ─────────────────────────────────────────────────────────────

@Composable
private fun TopupCard(topup: WalletTopup) {
    val (label, color) = when (topup.status) {
        WalletTopupStatus.COMPLETED ->
            "Thành công" to AccentGreen
        WalletTopupStatus.PENDING ->
            "Đang xử lý" to StatusColorsTheme.current.awaitingPayment
        WalletTopupStatus.FAILED ->
            "Thất bại" to MaterialTheme.colorScheme.error
        WalletTopupStatus.CANCELLED ->
            "Đã hủy" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    DetailCard(title = "Thông tin nạp tiền") {
        InfoRow(label = "Phương thức", value = "VNPay")
        Divider()
        StatusRow(label = "Trạng thái", text = label, tint = color)
        Divider()
        InfoRow(label = "Số tiền nạp", value = formatCurrencyVnd(topup.amount))
        topup.transactionId?.takeIf { it.isNotBlank() }?.let {
            Divider()
            InfoRow(label = "Mã VNPay", value = it, copyable = true)
        }
        topup.responseCode?.takeIf { it.isNotBlank() }?.let {
            Divider()
            InfoRow(label = "Mã phản hồi VNPay", value = it)
        }
        Divider()
        InfoRow(label = "Mã yêu cầu", value = topup.vnpTxnRef, copyable = true)
        topup.completedAt?.takeIf { it > 0L }?.let {
            Divider()
            InfoRow(
                label = "Thời điểm hoàn tất",
                value = it.toFormattedDate("dd/MM/yyyy HH:mm")
            )
        }
    }
}

// ─── Withdrawal card ─────────────────────────────────────────────────────────

@Composable
private fun WithdrawalCard(withdrawal: WalletWithdrawal) {
    val (label, color, icon) = when (withdrawal.status) {
        WalletWithdrawalStatus.PROCESSING -> Triple(
            "Đang xử lý",
            StatusColorsTheme.current.awaitingPayment,
            Icons.Outlined.HourglassEmpty
        )
        WalletWithdrawalStatus.COMPLETED -> Triple(
            "Đã chuyển khoản",
            AccentGreen,
            Icons.Outlined.CheckCircle
        )
        WalletWithdrawalStatus.REJECTED -> Triple(
            "Đã từ chối",
            MaterialTheme.colorScheme.error,
            Icons.Outlined.Schedule
        )
        WalletWithdrawalStatus.CANCELLED -> Triple(
            "Đã hủy",
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Outlined.Schedule
        )
    }

    DetailCard(title = "Thông tin rút tiền") {
        StatusRow(label = "Trạng thái", text = label, tint = color, leadingIcon = icon)
        Divider()
        InfoRow(label = "Số tiền rút", value = formatCurrencyVnd(withdrawal.amount))
        Divider()
        InfoRow(
            label = "Ngân hàng",
            value = withdrawal.bankName,
            icon = Icons.Outlined.AccountBalance
        )
        Divider()
        InfoRow(
            label = "Số tài khoản",
            value = withdrawal.bankAccountNumber,
            copyable = true
        )
        Divider()
        InfoRow(
            label = "Chủ tài khoản",
            value = withdrawal.bankAccountHolder
        )
        if (!withdrawal.note.isNullOrBlank()) {
            Divider()
            InfoRow(label = "Ghi chú", value = withdrawal.note!!)
        }
        if (!withdrawal.rejectionReason.isNullOrBlank()) {
            Divider()
            InfoRow(
                label = "Lý do từ chối",
                value = withdrawal.rejectionReason!!,
                emphasizeError = true
            )
        }
        withdrawal.completedAt?.takeIf { it > 0L }?.let {
            Divider()
            InfoRow(
                label = "Hoàn tất lúc",
                value = it.toFormattedDate("dd/MM/yyyy HH:mm")
            )
        }
    }
}

// ─── Shared building blocks ──────────────────────────────────────────────────

@Composable
private fun DetailCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (action != null) action()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    copyable: Boolean = false,
    copyValue: String = value,
    emphasizeError: Boolean = false
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (emphasizeError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
        }
        if (copyable) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(copyValue))
                    android.widget.Toast.makeText(
                        context, "Đã sao chép", android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Sao chép",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    text: String,
    tint: Color,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = tint.copy(alpha = 0.14f),
            contentColor = tint
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(Modifier.height(8.dp))
}

// ─── Error state ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Receipt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(10.dp)) {
                Text("Thử lại")
            }
        }
    }
}
