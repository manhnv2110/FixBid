package com.example.fixbid.presentation.worker.bids

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.components.StatusPill
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatRelativeTime
import com.example.fixbid.core.utils.formatShortDateTime
import com.example.fixbid.domain.model.BidStatus
import com.example.fixbid.domain.usecase.worker.MyBid
import com.example.fixbid.ui.theme.*

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

        // Filter chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
                            fontSize = 13.sp
                        )
                    }
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
                    EmptyBids()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
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
                        item { Spacer(Modifier.height(8.dp)) }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(text = statusLabel, color = statusColor)
                Text(
                    text = formatRelativeTime(bid.createdAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = booking?.category?.displayName ?: "Công việc",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (booking != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = booking.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = booking.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatShortDateTime(booking.scheduledAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Giá bạn báo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatCurrencyVnd(bid.proposedPrice),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Dự kiến ${bid.estimatedDurationHours}h",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Pending bids can be withdrawn
            if (bid.status == BidStatus.PENDING) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onWithdraw,
                    enabled = !isWithdrawing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isWithdrawing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Rút báo giá", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            } else if (bid.status == BidStatus.ACCEPTED) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bạn đã trúng thầu • Xem công việc",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentGreen
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBids() {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Gavel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Chưa có báo giá nào",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tìm việc phù hợp và gửi báo giá để bắt đầu nhận công việc",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun bidStatusInfo(status: BidStatus): Pair<String, Color> = when (status) {
    BidStatus.PENDING -> "Đang chờ duyệt" to StatusOrange
    BidStatus.ACCEPTED -> "Được chọn" to StatusGreen
    BidStatus.REJECTED -> "Không được chọn" to StatusGray
    BidStatus.WITHDRAWN -> "Đã rút" to StatusGray
}
