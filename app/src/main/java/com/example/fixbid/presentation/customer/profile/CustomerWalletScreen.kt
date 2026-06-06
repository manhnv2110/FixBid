package com.example.fixbid.presentation.customer.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.fixbid.domain.model.Wallet
import com.example.fixbid.domain.model.WalletTransaction
import com.example.fixbid.domain.model.WalletTransactionType
import com.example.fixbid.ui.theme.AccentGreen
import com.example.fixbid.ui.theme.StatusColorsTheme

/**
 * Customer-side wallet screen. Shows the customer's available balance, a
 * "coming soon" placeholder CTA for spending the balance on a future booking,
 * and the ledger of `wallet_transactions` filtered to the signed-in user.
 *
 * The most common entry that lands here is the `escrow_refund` row produced
 * by `fn_refund_escrow_to_customer` after a worker cancels a CONFIRMED booking
 * — see Requirements 6.1–6.8.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerWalletScreen(
    onBackClick: () -> Unit,
    onTransactionClick: (String) -> Unit = {},
    viewModel: CustomerWalletViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // One-shot events from the VM — we launch the VNPay URL in a browser
    // intent so VNPay's deep link can come back through `fixbid://vnpay-return`
    // and hit the existing VNPayReturnScreen, which will route the top-up
    // branch through `fn_credit_wallet_topup`.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WalletEvent.OpenTopupUrl -> {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(event.url)
                    )
                    context.startActivity(intent)
                }
                is WalletEvent.Toast -> {
                    android.widget.Toast.makeText(
                        context, event.message, android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

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
                // First-load skeleton: wallet not yet hydrated and no error.
                uiState.isLoading && uiState.wallet == null -> WalletSkeleton()

                // Hard error state — wallet failed to load and we have nothing to show.
                uiState.wallet == null && uiState.errorMessage != null -> {
                    WalletErrorState(
                        message = uiState.errorMessage!!,
                        onRetry = viewModel::refresh
                    )
                }

                // Happy path — render hero, action row, ledger.
                else -> WalletContent(
                    wallet = uiState.wallet,
                    transactions = uiState.transactions,
                    isLoading = uiState.isLoading,
                    onTransactionClick = onTransactionClick,
                    onTopupClick = viewModel::openTopupSheet,
                    onWithdrawClick = viewModel::openWithdrawSheet
                )
            }
        }
    }

    // Top-up sheet
    if (uiState.showTopupSheet) {
        WalletTopupSheet(
            isSubmitting = uiState.isTopupSubmitting,
            errorMessage = uiState.topupError,
            onConfirm = viewModel::submitTopup,
            onDismiss = viewModel::closeTopupSheet
        )
    }

    // Withdraw sheet
    if (uiState.showWithdrawSheet) {
        WalletWithdrawSheet(
            availableBalance = uiState.wallet?.balance ?: 0.0,
            isSubmitting = uiState.isWithdrawSubmitting,
            errorMessage = uiState.withdrawError,
            onConfirm = viewModel::submitWithdraw,
            onDismiss = viewModel::closeWithdrawSheet
        )
    }
}

// ─── Content ─────────────────────────────────────────────────────────────────

@Composable
private fun WalletContent(
    wallet: Wallet?,
    transactions: List<WalletTransaction>,
    isLoading: Boolean,
    onTransactionClick: (String) -> Unit,
    onTopupClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.add(
            WindowInsets(left = 16.dp, top = 16.dp, right = 16.dp, bottom = 16.dp)
        ).asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { BalanceHeroCard(wallet = wallet) }
        item {
            WalletActionsRow(
                wallet = wallet,
                onTopupClick = onTopupClick,
                onWithdrawClick = onWithdrawClick
            )
        }
        item { LedgerSectionHeader(count = transactions.size) }

        when {
            isLoading -> items(count = 3, key = { "skeleton-$it" }) { LedgerRowSkeleton() }
            transactions.isEmpty() -> item { EmptyTransactions() }
            else -> items(transactions, key = { it.id }) { tx ->
                LedgerRow(
                    transaction = tx,
                    walletUserId = wallet?.userId,
                    onClick = { onTransactionClick(tx.id) }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Hero card ───────────────────────────────────────────────────────────────

@Composable
private fun BalanceHeroCard(wallet: Wallet?) {
    val pending = wallet?.pendingBalance ?: 0.0
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
                        text = formatCurrencyVnd(wallet?.balance ?: 0.0),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Pending balance hint — surfaced when the customer has an in-flight
            // withdrawal request (pending > 0). Helps explain why their
            // available balance dropped without losing visibility on the locked
            // amount.
            if (pending > 0.0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.HourglassEmpty,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Đang tạm giữ ${formatCurrencyVnd(pending)} (yêu cầu rút đang xử lý)",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ─── Action row (Top-up + Withdraw) ──────────────────────────────────────────

@Composable
private fun WalletActionsRow(
    wallet: Wallet?,
    onTopupClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    val canWithdraw = (wallet?.balance ?: 0.0) >= 10_000.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // "Nạp tiền" — filled tonal style, primary intent.
        WalletActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.ArrowDownward,
            label = "Nạp tiền",
            subtitle = "Qua VNPay",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            enabled = true,
            onClick = onTopupClick
        )

        // "Rút tiền" — outlined-tonal, less visually loud than top-up
        // because the customer rarely withdraws compared to topping up.
        WalletActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.ArrowUpward,
            label = "Rút tiền",
            subtitle = if (canWithdraw) "Về tài khoản ngân hàng" else "Cần ít nhất 10.000đ",
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            enabled = canWithdraw,
            onClick = onWithdrawClick
        )
    }
}

@Composable
private fun WalletActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    subtitle: String,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) container else container.copy(alpha = 0.45f),
        contentColor = content,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(content.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) content else content.copy(alpha = 0.6f)
                )
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (enabled) content.copy(alpha = 0.75f) else content.copy(alpha = 0.5f)
            )
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
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "$count giao dịch",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

// ─── Ledger row ──────────────────────────────────────────────────────────────

@Composable
private fun LedgerRow(
    transaction: WalletTransaction,
    walletUserId: String?,
    onClick: () -> Unit
) {
    val style = ledgerStyleFor(transaction.type)
    // Direction sign per design: `+` when this row credits the wallet owner,
    // `-` when it debits. For customer-side `escrow_refund` the row's
    // `userId == wallet.userId` AND the type is a credit, so it shows `+`.
    val direction = signedDirection(
        type = transaction.type,
        ownsRow = walletUserId != null && transaction.userId == walletUserId
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    text = style.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (transaction.createdAt > 0L) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = transaction.createdAt.toFormattedDate("dd/MM/yyyy HH:mm"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                transaction.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatSignedAmount(direction, transaction.amount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = style.tint,
                    maxLines = 1
                )
                Text(
                    text = "Số dư: ${formatCurrencyVnd(transaction.balanceAfter)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private enum class Direction { POSITIVE, NEGATIVE, NEUTRAL }

private data class LedgerStyle(
    val label: String,
    val icon: ImageVector,
    val tint: Color
)

@Composable
private fun ledgerStyleFor(type: WalletTransactionType): LedgerStyle = when (type) {
    WalletTransactionType.ESCROW_REFUND -> LedgerStyle(
        label = "Hoàn tiền từ thợ",
        icon = Icons.Outlined.SwapHoriz,
        tint = AccentGreen
    )
    WalletTransactionType.ESCROW_HOLD -> LedgerStyle(
        label = "Đang giữ",
        icon = Icons.Outlined.HourglassEmpty,
        tint = StatusColorsTheme.current.awaitingPayment
    )
    WalletTransactionType.ESCROW_RELEASE -> LedgerStyle(
        label = "Đã giải ngân cho thợ",
        icon = Icons.Outlined.ArrowUpward,
        tint = StatusColorsTheme.current.neutral
    )
    WalletTransactionType.TOPUP -> LedgerStyle(
        label = "Nạp tiền vào ví",
        icon = Icons.Outlined.ArrowDownward,
        tint = AccentGreen
    )
    WalletTransactionType.WITHDRAWAL_REQUEST -> LedgerStyle(
        label = "Yêu cầu rút tiền (đang xử lý)",
        icon = Icons.Outlined.HourglassEmpty,
        tint = StatusColorsTheme.current.awaitingPayment
    )
    WalletTransactionType.WITHDRAWAL -> LedgerStyle(
        label = "Rút tiền thành công",
        icon = Icons.Outlined.ArrowUpward,
        tint = MaterialTheme.colorScheme.primary
    )
    WalletTransactionType.ADJUSTMENT -> LedgerStyle(
        label = "Điều chỉnh",
        icon = Icons.Outlined.SwapHoriz,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Direction of the signed amount label. The design rule: for rows that the
 * wallet owner owns ([ownsRow]) the sign reflects whether the type credits
 * (`+`) or debits (`-`) their wallet. For rows the user does not own (rare
 * — should not be returned by `getMyTransactions`), we fall back to neutral.
 */
private fun signedDirection(type: WalletTransactionType, ownsRow: Boolean): Direction {
    if (!ownsRow) return Direction.NEUTRAL
    return when (type) {
        // Customer-side credits.
        WalletTransactionType.ESCROW_REFUND -> Direction.POSITIVE
        WalletTransactionType.TOPUP -> Direction.POSITIVE
        // Outflows.
        WalletTransactionType.WITHDRAWAL -> Direction.NEGATIVE
        WalletTransactionType.WITHDRAWAL_REQUEST -> Direction.NEGATIVE
        // Neutral / informational.
        WalletTransactionType.ESCROW_HOLD -> Direction.NEUTRAL
        WalletTransactionType.ESCROW_RELEASE -> Direction.NEUTRAL
        WalletTransactionType.ADJUSTMENT -> Direction.NEUTRAL
    }
}

private fun formatSignedAmount(direction: Direction, amount: Double): String {
    val formatted = formatCurrencyVnd(amount)
    return when (direction) {
        Direction.POSITIVE -> "+$formatted"
        Direction.NEGATIVE -> "-$formatted"
        Direction.NEUTRAL -> formatted
    }
}

// ─── Empty / error / skeleton states ─────────────────────────────────────────

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
                text = "Lịch sử hoàn tiền sẽ xuất hiện tại đây",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WalletErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("Thử lại", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WalletSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero card skeleton
        SkeletonBlock(height = 130.dp, shape = RoundedCornerShape(22.dp))
        // Placeholder CTA skeleton
        SkeletonBlock(height = 52.dp, shape = RoundedCornerShape(14.dp))
        // Section header skeleton
        SkeletonBlock(height = 22.dp, shape = RoundedCornerShape(6.dp), widthFraction = 0.45f)
        // Ledger row skeletons
        repeat(3) { LedgerRowSkeleton() }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LedgerRowSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle skeleton — fixed size, not stretched.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(height = 14.dp, shape = RoundedCornerShape(4.dp), widthFraction = 0.6f)
                SkeletonBlock(height = 11.dp, shape = RoundedCornerShape(4.dp), widthFraction = 0.4f)
            }
            Spacer(Modifier.width(8.dp))
            // Right-edge amount skeleton — fixed width.
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )
        }
    }
}

@Composable
private fun SkeletonBlock(
    height: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}
