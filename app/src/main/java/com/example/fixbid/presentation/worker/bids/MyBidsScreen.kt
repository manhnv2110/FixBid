package com.example.fixbid.presentation.worker.bids

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatRelativeTime
import com.example.fixbid.core.utils.formatShortDateTime
import com.example.fixbid.domain.model.BidStatus
import com.example.fixbid.domain.usecase.worker.MyBid
import com.example.fixbid.ui.theme.AccentGreen
import com.example.fixbid.ui.theme.StatusColorsTheme

/**
 * Worker screen listing every bid the worker has submitted, grouped by status.
 *
 * Layout:
 *  - A summary stats row at the top (total / pending / accepted) so the
 *    worker sees their pipeline at a glance.
 *  - A horizontal filter bar with badge counts on Pending / Accepted.
 *  - Bid cards with a coloured status accent stripe down the leading edge,
 *    customer / job preview, and a footer that splits the bid amount and
 *    estimated duration into clearly labelled groups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBidsScreen(
    onBackClick: () -> Unit = {},
    onJobClick: (String) -> Unit = {},
    viewModel: MyBidsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var withdrawTarget by remember { mutableStateOf<MyBid?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MyBidsEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppHeader(title = "Báo giá của tôi", onBackClick = onBackClick)

        // Pipeline summary — visible even when the list is empty so the worker
        // sees the lifetime context, not just the currently filtered slice.
        if (!uiState.isLoading && uiState.allBids.isNotEmpty()) {
            BidsSummaryStrip(
                total = uiState.allBids.size,
                pending = uiState.pendingCount,
                accepted = uiState.acceptedCount
            )
        }

        // Filter chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MyBidsFilter.entries.toList()) { filter ->
                val count = when (filter) {
                    MyBidsFilter.PENDING -> uiState.pendingCount
                    MyBidsFilter.ACCEPTED -> uiState.acceptedCount
                    else -> null
                }
                FilterChip(
                    selected = uiState.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = {
                        Text(
                            text = if (count != null && count > 0) "${filter.label} ($count)" else filter.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    shape = RoundedCornerShape(50)
                )
            }
        }

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            uiState.errorMessage != null && uiState.allBids.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp), Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.load() }) { Text("Thử lại") }
                }
            }
            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                val bids = uiState.filteredBids
                if (bids.isEmpty()) {
                    EmptyBids(filter = uiState.filter)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(bids, key = { it.bid.id }) { myBid ->
                            MyBidCard(
                                myBid = myBid,
                                isWithdrawing = uiState.withdrawingId == myBid.bid.id,
                                onClick = { onJobClick(myBid.bid.bookingId) },
                                onWithdraw = { withdrawTarget = myBid }
                            )
                        }
                    }
                }
            }
        }
    }

    withdrawTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { withdrawTarget = null },
            title = { Text("Rút báo giá?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn sẽ rút báo giá cho công việc \"${target.booking?.category?.displayName ?: "này"}\". Hành động này không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.withdraw(target.bid.id)
                    withdrawTarget = null
                }) {
                    Text("Rút báo giá", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { withdrawTarget = null }) { Text("Huỷ") }
            }
        )
    }
}

// ─── Summary strip ───────────────────────────────────────────────────────────

@Composable
private fun BidsSummaryStrip(
    total: Int,
    pending: Int,
    accepted: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Gavel,
            value = "$total",
            label = "Tổng báo giá",
            tint = MaterialTheme.colorScheme.primary
        )
        SummaryTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.AccessTime,
            value = "$pending",
            label = "Đang chờ",
            tint = StatusColorsTheme.current.pending
        )
        SummaryTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.CheckCircle,
            value = "$accepted",
            label = "Được chọn",
            tint = AccentGreen
        )
    }
}

@Composable
private fun SummaryTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Bid card ────────────────────────────────────────────────────────────────

@Composable
private fun MyBidCard(
    myBid: MyBid,
    isWithdrawing: Boolean,
    onClick: () -> Unit,
    onWithdraw: () -> Unit
) {
    val bid = myBid.bid
    val booking = myBid.booking
    val (statusLabel, statusColor) = bidStatusInfo(bid.status)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Status accent stripe — at-a-glance signal.
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )
            Column(Modifier.padding(16.dp)) {
                // Header: status pill + relative time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(label = statusLabel, color = statusColor)
                    Text(
                        text = formatRelativeTime(bid.createdAt),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Customer block
                if (booking?.customer?.fullName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (booking.customer.fullName.firstOrNull() ?: '?').uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = booking.customer.fullName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Title + description
                Text(
                    text = booking?.category?.displayName ?: "Công việc",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (booking != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = booking.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    MetaRow(icon = Icons.Outlined.LocationOn, text = booking.address)
                    Spacer(Modifier.height(4.dp))
                    MetaRow(
                        icon = Icons.Outlined.Schedule,
                        text = formatShortDateTime(booking.scheduledAt)
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))

                // Footer — split bid amount / duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Giá bạn báo",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatCurrencyVnd(bid.proposedPrice),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Thời lượng",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${bid.estimatedDurationHours}h",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Trailing action depends on status
                when (bid.status) {
                    BidStatus.PENDING -> {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onWithdraw,
                            enabled = !isWithdrawing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            if (isWithdrawing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Rút báo giá", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                    BidStatus.ACCEPTED -> {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(
                                text = "Bạn đã trúng thầu • Mở việc",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun MetaRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyBids(filter: MyBidsFilter) {
    val (title, subtitle) = when (filter) {
        MyBidsFilter.ALL -> "Chưa có báo giá nào" to
            "Tìm việc phù hợp và gửi báo giá để bắt đầu nhận công việc"
        MyBidsFilter.PENDING -> "Không có báo giá đang chờ" to
            "Khi bạn gửi báo giá mới, chúng sẽ hiện ở đây"
        MyBidsFilter.ACCEPTED -> "Chưa có báo giá nào trúng" to
            "Cứ kiên trì — báo giá phù hợp giá và thời gian sẽ được khách chọn"
        MyBidsFilter.REJECTED -> "Không có báo giá nào trong mục này" to
            "Báo giá bị từ chối hoặc đã rút sẽ hiện ở đây"
    }
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun bidStatusInfo(status: BidStatus): Pair<String, Color> {
    val sc = StatusColorsTheme.current
    return when (status) {
        BidStatus.PENDING -> "Đang chờ duyệt" to sc.pending
        BidStatus.ACCEPTED -> "Được chọn" to sc.positive
        BidStatus.REJECTED -> "Không được chọn" to sc.neutral
        BidStatus.WITHDRAWN -> "Đã rút" to sc.neutral
    }
}
