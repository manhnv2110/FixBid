package com.example.fixbid.presentation.customer.history

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.utils.toFormattedDate
import com.example.fixbid.core.utils.toRelativeTime
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.core.components.PrimaryTopBar
import com.example.fixbid.core.components.StatusPill
import com.example.fixbid.ui.theme.statusColor
import com.example.fixbid.ui.theme.statusLabel
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingHistoryScreen(
    onBookingClick: (String) -> Unit,
    onBiddingWorkersClick: (String) -> Unit,
    onCompletionConfirmClick: (String) -> Unit = {},
    onPaymentClick: (String) -> Unit = {},
    onReviewClick: (String) -> Unit = {},
    onWorkerProfileClick: (String) -> Unit = {},
    viewModel: BookingHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Đang xử lý", "Hoàn thành", "Đã hủy")

    // Auto refresh khi screen hiển thị lại
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        PrimaryTopBar(title = "Đơn dịch vụ")

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val count = when (val state = uiState) {
                    is HistoryUiState.Success -> when (index) {
                        0 -> state.activeBookings.size
                        1 -> state.completedBookings.size
                        else -> state.cancelledBookings.size
                    }
                    else -> 0
                }
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = count.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTab == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        // Content
        when (val state = uiState) {
            is HistoryUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Đang tải...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
            is HistoryUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = viewModel::loadBookings,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử lại")
                        }
                    }
                }
            }
            is HistoryUiState.Success -> {
                val items = when (selectedTab) {
                    0 -> state.activeBookings
                    1 -> state.completedBookings
                    else -> state.cancelledBookings
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(tab = selectedTab)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items = items, key = { it.id }) { booking ->
                                BookingCard(
                                    booking = booking,
                                    isDone = selectedTab == 1,
                                    isCancelled = selectedTab == 2,
                                    onClick = { onBookingClick(booking.id) },
                                    onBiddingWorkersClick = { onBiddingWorkersClick(booking.id) },
                                    onPaymentClick = { onPaymentClick(booking.id) },
                                    onCompletionConfirmClick = { onCompletionConfirmClick(booking.id) },
                                    onReviewClick = { onReviewClick(booking.id) },
                                    onWorkerProfileClick = {
                                        if (booking.workerId.isNotBlank()) onWorkerProfileClick(booking.workerId)
                                    }
                                )
                            }
                            // Bottom spacing
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(tab: Int) {
    val (icon, title, subtitle) = when (tab) {
        0 -> Triple(
            Icons.Outlined.Assignment,
            "Chưa có đơn nào đang xử lý",
            "Đặt dịch vụ ngay để bắt đầu!"
        )
        1 -> Triple(
            Icons.Outlined.CheckCircleOutline,
            "Chưa có đơn hoàn thành",
            "Các đơn hoàn thành sẽ hiển thị ở đây"
        )
        else -> Triple(
            Icons.Outlined.Block,
            "Chưa có đơn bị hủy",
            "Đơn bị hủy hoặc tranh chấp sẽ hiển thị ở đây"
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    isDone: Boolean,
    isCancelled: Boolean = false,
    onClick: () -> Unit,
    onBiddingWorkersClick: () -> Unit,
    onPaymentClick: () -> Unit,
    onCompletionConfirmClick: () -> Unit,
    onReviewClick: () -> Unit = {},
    onWorkerProfileClick: () -> Unit = {}
) {
    val statusInfo = getStatusInfo(booking.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top: Category icon + name + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(booking.category),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.category.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = booking.createdAt.toRelativeTime(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status badge
                StatusPill(
                    text = statusInfo.label,
                    color = statusInfo.color
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = booking.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Address row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = booking.address,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Cancellation summary — surfaced on the "Đã hủy" tab so the
            // customer sees the reason and a refund hint without needing to
            // open the booking detail. Detail screen still has the full
            // `WorkerCancelledRefundBanner` with the "Xem ví" CTA.
            if (isCancelled && booking.status == BookingStatus.CANCELLED &&
                !booking.cancelReason.isNullOrBlank()
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lý do hủy: ${booking.cancelReason}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }
            }

            // Bottom section based on status
            if (booking.status == BookingStatus.BIDDING) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onBiddingWorkersClick)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Xem danh sách báo giá từ thợ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (booking.status == BookingStatus.AWAITING_PAYMENT) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPaymentClick)
                        .background(statusInfo.color.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Payments,
                            contentDescription = null,
                            tint = statusInfo.color,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Bấm để tiến hành thanh toán",
                            fontSize = 12.sp,
                            color = statusInfo.color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = statusInfo.color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (booking.status == BookingStatus.PENDING_COMPLETION) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCompletionConfirmClick)
                        .background(statusInfo.color.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = statusInfo.color,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Thợ đã báo xong, bấm để xác nhận",
                            fontSize = 12.sp,
                            color = statusInfo.color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = statusInfo.color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isDone && booking.agreedPrice != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (booking.worker != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = booking.workerId.isNotBlank()) {
                                    onWorkerProfileClick()
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = booking.worker.fullName.first().toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = booking.worker.fullName,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            if (booking.workerId.isNotBlank()) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Xem hồ sơ thợ",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${java.text.NumberFormat.getNumberInstance(java.util.Locale("vi", "VN")).format(booking.agreedPrice.toLong())} đ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Review CTA for completed bookings that have an assigned worker
            if (booking.status == BookingStatus.COMPLETED && booking.workerId.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onReviewClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Outlined.StarRate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Đánh giá thợ",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private data class StatusInfo(val label: String, val color: Color)

@Composable
private fun getStatusInfo(status: BookingStatus): StatusInfo =
    StatusInfo(statusLabel(status), statusColor(status))

private fun getCategoryIcon(category: ServiceCategory): ImageVector = when (category) {
    ServiceCategory.PLUMBING -> Icons.Outlined.Plumbing
    ServiceCategory.ELECTRICAL -> Icons.Outlined.ElectricalServices
    ServiceCategory.CARPENTRY -> Icons.Outlined.Carpenter
    ServiceCategory.AIR_CONDITIONING -> Icons.Outlined.AcUnit
    ServiceCategory.APPLIANCE_REPAIR -> Icons.Outlined.Kitchen
    ServiceCategory.CLEANING -> Icons.Outlined.CleaningServices
    ServiceCategory.LOCKSMITH -> Icons.Outlined.Lock
    ServiceCategory.ROOFING -> Icons.Outlined.Roofing
    ServiceCategory.OTHER -> Icons.Outlined.Build
}