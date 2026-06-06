package com.example.fixbid.presentation.worker.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.WorkOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatRelativeTime
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.presentation.worker.components.EmptyStateCard
import com.example.fixbid.presentation.worker.components.WorkerJobCard
import com.example.fixbid.presentation.worker.home.WorkerHomeViewModel
import com.example.fixbid.ui.theme.*

/**
 * Tab "Việc làm" của worker — tách thành màn riêng để gọn code.
 * - 3 tab: Đang chạy (Active + Pending) / Đã xong (Completed) / Đã hủy (Cancelled)
 * - PullToRefresh
 * - Auto-reload khi quay lại từ JobDetail (lifecycle ON_RESUME) nên thợ
 *   không phải tự kéo refresh sau khi hủy/hoàn thành công việc.
 * - Đồng nhất với BookingHistoryScreen của customer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerMyWorkScreen(
    onJobClick: (String) -> Unit,
    onStartJob: (String) -> Unit,
    onCompleteJob: (String) -> Unit,
    viewModel: WorkerHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val tabs = listOf("Đang chạy", "Đã xong", "Đã hủy")

    // Reset isRefreshing khi data load xong
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) isRefreshing = false
    }

    // Refresh khi user quay lại màn — đảm bảo sau khi hủy/hoàn thành ở
    // JobDetail, list hiển thị đúng trạng thái mới ngay lập tức thay vì
    // chờ user pull-to-refresh hoặc đăng nhập lại.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadDashboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppHeader(title = "Việc làm của tôi")

        val activeCount = uiState.activeJobs.size + uiState.pendingJobs.size
        val doneCount = uiState.completedJobs.size
        val cancelledCount = uiState.cancelledJobs.size

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
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                val count = when (index) {
                    0 -> activeCount
                    1 -> doneCount
                    else -> cancelledCount
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

        if (uiState.isLoading &&
            uiState.activeJobs.isEmpty() &&
            uiState.completedJobs.isEmpty() &&
            uiState.cancelledJobs.isEmpty()
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadDashboard()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> ActiveTab(
                    activeJobs = uiState.activeJobs,
                    pendingJobs = uiState.pendingJobs,
                    onJobClick = onJobClick,
                    onStartJob = onStartJob,
                    onCompleteJob = onCompleteJob
                )
                1 -> DoneTab(
                    completedJobs = uiState.completedJobs,
                    onJobClick = onJobClick
                )
                else -> CancelledTab(
                    cancelledJobs = uiState.cancelledJobs,
                    onJobClick = onJobClick
                )
            }
        }
    }
}

@Composable
private fun ActiveTab(
    activeJobs: List<Booking>,
    pendingJobs: List<Booking>,
    onJobClick: (String) -> Unit,
    onStartJob: (String) -> Unit,
    onCompleteJob: (String) -> Unit
) {
    if (activeJobs.isEmpty() && pendingJobs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Outlined.WorkOff,
                title = "Chưa có việc nào đang chạy",
                message = "Đặt thầu các yêu cầu mới để bắt đầu nhận việc",
                asCard = false
            )
        }
        return
    }

    val pendingCompletion = activeJobs.filter { it.status == BookingStatus.PENDING_COMPLETION }
    val inProgress = activeJobs.filter { it.status == BookingStatus.IN_PROGRESS }
    // pendingJobs đã là CONFIRMED

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hiển thị theo thứ tự ưu tiên: đang làm → chờ khách xác nhận → đã xác nhận
        if (inProgress.isNotEmpty()) {
            items(inProgress, key = { it.id }) { booking ->
                WorkerJobCard(
                    booking = booking,
                    statusColor = statusColor(BookingStatus.IN_PROGRESS),
                    statusLabel = "Đang làm",
                    onClick = { onJobClick(booking.id) },
                    actionLabel = "Báo hoàn thành",
                    actionColor = AccentGreen,
                    onActionClick = { onJobClick(booking.id) }
                )
            }
        }
        if (pendingCompletion.isNotEmpty()) {
            items(pendingCompletion, key = { it.id }) { booking ->
                WorkerJobCard(
                    booking = booking,
                    statusColor = statusColor(BookingStatus.PENDING_COMPLETION),
                    statusLabel = "Chờ khách xác nhận",
                    onClick = { onJobClick(booking.id) }
                )
            }
        }
        if (pendingJobs.isNotEmpty()) {
            items(pendingJobs, key = { it.id }) { booking ->
                WorkerJobCard(
                    booking = booking,
                    statusColor = statusColor(BookingStatus.CONFIRMED),
                    statusLabel = "Đã xác nhận",
                    onClick = { onJobClick(booking.id) },
                    actionLabel = "Bắt đầu làm",
                    onActionClick = { onStartJob(booking.id) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun DoneTab(
    completedJobs: List<Booking>,
    onJobClick: (String) -> Unit
) {
    if (completedJobs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Outlined.AssignmentTurnedIn,
                title = "Chưa có việc hoàn thành",
                message = "Việc đã hoàn thành sẽ hiển thị tại đây",
                asCard = false
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(completedJobs.sortedByDescending { it.updatedAt }, key = { it.id }) { booking ->
            CompletedJobRow(booking = booking, onClick = { onJobClick(booking.id) })
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun CompletedJobRow(
    booking: Booking,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.example.fixbid.core.components.StatusPill(
                    text = "Hoàn thành",
                    color = statusColor(BookingStatus.COMPLETED)
                )
                Text(
                    text = formatRelativeTime(booking.updatedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = booking.category.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = booking.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = booking.customer?.fullName ?: "Khách hàng",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = booking.agreedPrice?.let { formatCurrencyVnd(it) } ?: "—",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CancelledTab(
    cancelledJobs: List<Booking>,
    onJobClick: (String) -> Unit
) {
    if (cancelledJobs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Outlined.Block,
                title = "Chưa có việc đã hủy",
                message = "Việc bị hủy sẽ hiển thị tại đây để bạn tra cứu lại",
                asCard = false
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(cancelledJobs.sortedByDescending { it.updatedAt }, key = { it.id }) { booking ->
            CancelledJobRow(booking = booking, onClick = { onJobClick(booking.id) })
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

/**
 * Row variant for cancelled bookings — surfaces the status pill, the cancel
 * reason (truncated to 2 lines so the list stays scannable) and the agreed
 * price the worker was supposed to receive. Matches the visual rhythm of
 * [CompletedJobRow] so the three tabs feel like one consistent list.
 */
@Composable
private fun CancelledJobRow(
    booking: Booking,
    onClick: () -> Unit
) {
    val cancelledColor = statusColor(BookingStatus.CANCELLED)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.example.fixbid.core.components.StatusPill(
                    text = "Đã hủy",
                    color = cancelledColor
                )
                Text(
                    text = formatRelativeTime(booking.updatedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = booking.category.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Show the cancel reason if available — that's the value the
            // worker most likely came back here for.
            if (!booking.cancelReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lý do: ${booking.cancelReason}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = booking.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = booking.customer?.fullName ?: "Khách hàng",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = booking.agreedPrice?.let { formatCurrencyVnd(it) } ?: "—",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
