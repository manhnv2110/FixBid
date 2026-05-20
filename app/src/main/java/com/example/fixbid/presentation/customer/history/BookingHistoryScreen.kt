package com.example.fixbid.presentation.customer.history

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.fixbid.ui.theme.BackgroundGray
import com.example.fixbid.ui.theme.LightBlue
import com.example.fixbid.ui.theme.PrimaryBlue
import com.example.fixbid.ui.theme.TextPrimary
import com.example.fixbid.ui.theme.TextSecondary
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingHistoryScreen(
    onBookingClick: (String) -> Unit,
    onCompletionConfirmClick: (String) -> Unit = {},
    viewModel: BookingHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Đang xử lý", "Hoàn thành")

    // Auto refresh khi screen hiển thị lại
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryBlue)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Đơn dịch vụ",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = PrimaryBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    height = 3.dp,
                    color = PrimaryBlue
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                val count = when (val state = uiState) {
                    is HistoryUiState.Success -> {
                        if (index == 0) state.activeBookings.size else state.completedBookings.size
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
                                color = if (selectedTab == index) PrimaryBlue else TextSecondary,
                                fontSize = 14.sp
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selectedTab == index) PrimaryBlue else Color(0xFFE0E0E0)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = count.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTab == index) Color.White else TextSecondary
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
                        CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Đang tải...", color = TextSecondary, fontSize = 14.sp)
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
                            tint = Color(0xFFB0BEC5),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            state.message,
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = viewModel::loadBookings,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử lại")
                        }
                    }
                }
            }
            is HistoryUiState.Success -> {
                val items = if (selectedTab == 0) state.activeBookings else state.completedBookings

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
                            EmptyState(isActive = selectedTab == 0)
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
                                    onClick = {
                                        when (booking.status) {
                                            BookingStatus.BIDDING -> onBookingClick(booking.id)
                                            BookingStatus.PENDING_COMPLETION -> onCompletionConfirmClick(booking.id)
                                            else -> { /* no action for other statuses */ }
                                        }
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
private fun EmptyState(isActive: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = if (isActive) Icons.Outlined.Assignment else Icons.Outlined.CheckCircleOutline,
            contentDescription = null,
            tint = Color(0xFFB0BEC5),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isActive) "Chưa có đơn nào đang xử lý" else "Chưa có đơn hoàn thành",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isActive) "Đặt dịch vụ ngay để bắt đầu!" else "Các đơn hoàn thành sẽ hiển thị ở đây",
            fontSize = 13.sp,
            color = Color(0xFFB0BEC5)
        )
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    isDone: Boolean,
    onClick: () -> Unit
) {
    val statusInfo = getStatusInfo(booking.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = booking.status == BookingStatus.BIDDING ||
                        booking.status == BookingStatus.PENDING_COMPLETION,
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(LightBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(booking.category),
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.category.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = booking.createdAt.toRelativeTime(),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusInfo.color.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = statusInfo.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusInfo.color
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = booking.description,
                fontSize = 13.sp,
                color = TextSecondary,
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
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = booking.address,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom section based on status
            if (booking.status == BookingStatus.BIDDING) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF5F5F5))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Gavel,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Đang chờ báo giá từ thợ",
                            fontSize = 12.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (booking.status == BookingStatus.PENDING_COMPLETION) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF5F5F5))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Thợ đã báo xong, bấm để xác nhận",
                            fontSize = 12.sp,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isDone && booking.agreedPrice != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF5F5F5))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (booking.worker != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = booking.worker.fullName.first().toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF43A047)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = booking.worker.fullName,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Text(
                        text = "${java.text.NumberFormat.getNumberInstance(java.util.Locale("vi", "VN")).format(booking.agreedPrice.toLong())} đ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
            }
        }
    }
}

private data class StatusInfo(val label: String, val color: Color)

private fun getStatusInfo(status: BookingStatus): StatusInfo = when (status) {
    BookingStatus.BIDDING -> StatusInfo("Chờ báo giá", Color(0xFF00897B))
    BookingStatus.PENDING -> StatusInfo("Chờ xác nhận", Color(0xFFFFA000))
    BookingStatus.CONFIRMED -> StatusInfo("Đã xác nhận", Color(0xFF1565C0))
    BookingStatus.IN_PROGRESS -> StatusInfo("Đang làm", Color(0xFF6A1B9A))
    BookingStatus.PENDING_COMPLETION -> StatusInfo("Chờ xác nhận hoàn thành", Color(0xFFE65100))
    BookingStatus.COMPLETED -> StatusInfo("Hoàn thành", Color(0xFF43A047))
    BookingStatus.CANCELLED -> StatusInfo("Đã huỷ", Color(0xFFB0BEC5))
    BookingStatus.DISPUTED -> StatusInfo("Tranh chấp", Color(0xFFD32F2F))
}

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