package com.example.fixbid.presentation.worker.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.toFormattedDate
import com.example.fixbid.domain.model.WalletTransactionType
import com.example.fixbid.ui.theme.AccentGreen
import com.example.fixbid.ui.theme.StatusOrange
import com.example.fixbid.ui.theme.StatusRed

/**
 * Worker wallet — backed by the real `wallets` + `wallet_transactions`
 * tables. Shows:
 *  - Hero card with available balance and pending strip.
 *  - Quick stats row (30-day earnings + lifetime payout count).
 *  - Ledger list — each row is a real wallet_transactions entry.
 *
 * The "Rút tiền" CTA is intentionally disabled until a real disbursement
 * endpoint is integrated. The ledger ledger view itself is fully wired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerWalletScreen(
    onBackClick: () -> Unit,
    onTransactionClick: (bookingId: String) -> Unit = {},
    viewModel: WorkerWalletViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppHeader(title = "Ví của tôi", onBackClick = onBackClick) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                uiState.wallet == null && uiState.errorMessage != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = viewModel::refresh) { Text("Thử lại") }
                        }
                    }
                }
                else -> WalletContent(
                    state = uiState,
                    onTransactionClick = onTransactionClick
                )
            }
        }
    }
}

// ─── Content ─────────────────────────────────────────────────────────────────

@Composable
private fun WalletContent(
    state: WorkerWalletUiState,
    onTransactionClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { BalanceHeroCard(state = state) }
        item { QuickStatsRow(state = state) }
        item { LedgerSectionHeader(count = state.transactions.size) }

        if (state.transactions.isEmpty()) {
            item { EmptyTransactions() }
        } else {
            items(state.transactions, key = { it.transaction.id }) { row ->
                LedgerRow(
                    row = row,
                    onClick = {
                        row.transaction.bookingId?.let(onTransactionClick)
                    }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Balance hero ────────────────────────────────────────────────────────────

@Composable
private fun BalanceHeroCard(state: WorkerWalletUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Số dư khả dụng",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatCurrencyVnd(state.balance),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Pending strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.HourglassEmpty,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Đang giữ trong hệ thống",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                        Text(
                            text = formatCurrencyVnd(state.pendingBalance),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        text = "Chờ xác nhận",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { /* withdrawal endpoint not integrated yet */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                enabled = false,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            ) {
                Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Rút tiền (sắp ra mắt)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ─── Quick stats ─────────────────────────────────────────────────────────────

@Composable
private fun QuickStatsRow(state: WorkerWalletUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Payments,
            value = formatCurrencyVnd(state.monthlyEarnings),
            label = "Thu nhập 30 ngày",
            tint = AccentGreen
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Receipt,
            value = "${state.payoutCount}",
            label = "Lượt thanh toán",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ─── Section header ──────────────────────────────────────────────────────────

@Composable
private fun LedgerSectionHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Lịch sử giao dịch",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$count giao dịch",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Ledger row ──────────────────────────────────────────────────────────────

@Composable
private fun LedgerRow(
    row: WalletLedgerRow,
    onClick: () -> Unit
) {
    val tx = row.transaction
    val style = ledgerStyleFor(tx.type)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = tx.bookingId != null, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(style.tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    style.icon,
                    contentDescription = null,
                    tint = style.tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.bookingTitle ?: style.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(style.tint.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = style.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = style.tint
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    if (tx.createdAt > 0L) {
                        Text(
                            text = tx.createdAt.toFormattedDate("HH:mm dd/MM/yyyy"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                row.customerName?.takeIf { it.isNotBlank() }?.let { name ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Khách: $name",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                tx.reference?.takeIf { it.isNotBlank() }?.let { ref ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Mã GD: $ref",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = style.signedAmount(tx.amount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = style.tint,
                    maxLines = 1
                )
                Text(
                    text = "Số dư: ${formatCurrencyVnd(tx.balanceAfter)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private data class LedgerStyle(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val direction: Direction
) {
    enum class Direction { POSITIVE, NEGATIVE, NEUTRAL }

    fun signedAmount(amount: Double): String {
        val formatted = formatCurrencyVnd(amount)
        return when (direction) {
            Direction.POSITIVE -> "+$formatted"
            Direction.NEGATIVE -> "-$formatted"
            Direction.NEUTRAL -> formatted
        }
    }
}

@Composable
private fun ledgerStyleFor(type: WalletTransactionType): LedgerStyle = when (type) {
    WalletTransactionType.ESCROW_HOLD -> LedgerStyle(
        label = "Đang giữ",
        icon = Icons.Outlined.AccessTime,
        tint = StatusOrange,
        direction = LedgerStyle.Direction.NEUTRAL
    )
    WalletTransactionType.ESCROW_RELEASE -> LedgerStyle(
        label = "Nhận thanh toán",
        icon = Icons.Outlined.ArrowDownward,
        tint = AccentGreen,
        direction = LedgerStyle.Direction.POSITIVE
    )
    WalletTransactionType.ESCROW_REFUND -> LedgerStyle(
        label = "Hoàn tiền cho khách",
        icon = Icons.Outlined.SwapHoriz,
        tint = StatusRed,
        direction = LedgerStyle.Direction.NEGATIVE
    )
    WalletTransactionType.WITHDRAWAL -> LedgerStyle(
        label = "Rút tiền",
        icon = Icons.Outlined.ArrowUpward,
        tint = MaterialTheme.colorScheme.primary,
        direction = LedgerStyle.Direction.NEGATIVE
    )
    WalletTransactionType.ADJUSTMENT -> LedgerStyle(
        label = "Điều chỉnh",
        icon = Icons.Outlined.SwapHoriz,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        direction = LedgerStyle.Direction.NEUTRAL
    )
}

@Composable
private fun EmptyTransactions() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Chưa có giao dịch nào",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Hoàn thành công việc để bắt đầu nhận thanh toán",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
