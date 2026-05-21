package com.example.fixbid.presentation.worker.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.fixbid.core.components.SectionHeader
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatRelativeTime
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.presentation.customer.profile.ProfileScreen
import com.example.fixbid.presentation.worker.components.EmptyStateCard
import com.example.fixbid.presentation.worker.components.WorkerBottomNavbar
import com.example.fixbid.presentation.worker.components.WorkerJobCard
import com.example.fixbid.presentation.worker.jobs.WorkerMyWorkScreen
import com.example.fixbid.ui.theme.*

@Composable
fun WorkerHomeScreen(
    onNotificationClick: () -> Unit = {},
    onJobClick: (String) -> Unit = {},
    onJobRequestClick: (String) -> Unit = {},
    onBrowseAllRequestsClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    showWorkTab: Boolean = false,
    viewModel: WorkerHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(showWorkTab) {
        if (showWorkTab) selectedNavIndex = 1
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            WorkerBottomNavbar(
                selectedIndex = selectedNavIndex,
                onItemSelected = { selectedNavIndex = it }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when (selectedNavIndex) {
            1 -> Box(modifier = Modifier.padding(innerPadding)) {
                WorkerMyWorkScreen(
                    onJobClick = onJobClick,
                    onStartJob = viewModel::startJob,
                    onCompleteJob = viewModel::completeJob
                )
            }
            2 -> Box(modifier = Modifier.padding(innerPadding)) {
                ProfileScreen(onSignOut = onSignOut)
            }
            else -> WorkerDashboard(
                uiState = uiState,
                bottomPadding = innerPadding.calculateBottomPadding(),
                onNotificationClick = onNotificationClick,
                onToggleAvailability = viewModel::toggleAvailability,
                onRetry = viewModel::loadDashboard,
                onJobClick = onJobClick,
                onJobRequestClick = onJobRequestClick,
                onBrowseAllRequestsClick = onBrowseAllRequestsClick,
                onSeeAllWork = { selectedNavIndex = 1 },
                onStartJob = viewModel::startJob,
                onCompleteJob = viewModel::completeJob
            )
        }
    }
}

// ─── Dashboard ────────────────────────────────────────────────────────────────

@Composable
private fun WorkerDashboard(
    uiState: WorkerHomeUiState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onNotificationClick: () -> Unit,
    onToggleAvailability: () -> Unit,
    onRetry: () -> Unit,
    onJobClick: (String) -> Unit,
    onJobRequestClick: (String) -> Unit,
    onBrowseAllRequestsClick: () -> Unit,
    onSeeAllWork: () -> Unit,
    onStartJob: (String) -> Unit,
    onCompleteJob: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DashboardHeader(
            userName = uiState.userName,
            isAvailable = uiState.isAvailable,
            isToggling = uiState.isTogglingAvailability,
            onToggleAvailability = onToggleAvailability,
            onNotificationClick = onNotificationClick
        )

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            uiState.errorMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onRetry) {
                            Text("Thử lại", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = bottomPadding + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    EarningsHeroCard(
                        monthlyEarnings = uiState.monthlyEarnings,
                        completedCount = uiState.completedCount,
                        rating = uiState.profile?.averageRating ?: 0.0,
                        totalReviews = uiState.profile?.totalReviews ?: 0,
                        isVerified = uiState.profile?.identityVerified ?: false
                    )

                    OpenRequestsSection(
                        requests = uiState.openRequests,
                        onItemClick = onJobRequestClick,
                        onSeeAll = onBrowseAllRequestsClick
                    )

                    ActiveWorkSection(
                        activeJobs = uiState.activeJobs,
                        pendingJobs = uiState.pendingJobs,
                        onJobClick = onJobClick,
                        onStartJob = onStartJob,
                        onCompleteJob = onCompleteJob,
                        onSeeAll = onSeeAllWork
                    )

                    if (uiState.profile?.identityVerified == false) {
                        VerifyTipBanner()
                    }
                }
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    userName: String,
    isAvailable: Boolean,
    isToggling: Boolean,
    onToggleAvailability: () -> Unit,
    onNotificationClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Xin chào,",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
                Text(
                    text = userName.ifEmpty { "Thợ dịch vụ" },
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onNotificationClick) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Thông báo",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Availability card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAvailable) AccentGreen else StatusGray)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isAvailable) "Đang sẵn sàng nhận việc" else "Đang tạm nghỉ",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isAvailable) "Khách hàng có thể tìm thấy bạn"
                            else "Bật để bắt đầu nhận yêu cầu",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                }
                Switch(
                    checked = isAvailable,
                    onCheckedChange = { onToggleAvailability() },
                    enabled = !isToggling,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                    )
                )
            }
        }
    }
}

// ─── Earnings hero ────────────────────────────────────────────────────────────

@Composable
private fun EarningsHeroCard(
    monthlyEarnings: Double,
    completedCount: Int,
    rating: Double,
    totalReviews: Int,
    isVerified: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thu nhập 30 ngày",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isVerified) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Đã xác minh",
                            fontSize = 11.sp,
                            color = AccentGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatCurrencyVnd(monthlyEarnings),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CheckCircle,
                    iconTint = AccentGreen,
                    value = "$completedCount",
                    label = "Hoàn thành"
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                MetricCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Star,
                    iconTint = StatusGold,
                    value = if (rating > 0) "%.1f".format(rating) else "—",
                    label = if (totalReviews > 0) "$totalReviews đánh giá" else "Chưa có"
                )
            }
        }
    }
}

@Composable
private fun MetricCell(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String
) {
    Row(
        modifier = modifier.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Open requests preview ────────────────────────────────────────────────────

@Composable
private fun OpenRequestsSection(
    requests: List<Booking>,
    onItemClick: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    Column {
        SectionHeader(
            title = "Yêu cầu mới cho bạn",
            actionLabel = "Xem tất cả",
            onActionClick = onSeeAll
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (requests.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Outlined.Inbox,
                title = "Chưa có yêu cầu phù hợp",
                message = "Khi có yêu cầu mới đúng kỹ năng của bạn, chúng sẽ hiện ở đây",
                actionLabel = "Khám phá tất cả",
                onActionClick = onSeeAll
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                requests.take(3).forEach { booking ->
                    RequestPreviewCard(
                        booking = booking,
                        onClick = { onItemClick(booking.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestPreviewCard(
    booking: Booking,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = booking.category.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatRelativeTime(booking.createdAt),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = booking.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = booking.address,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = booking.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Thoả thuận",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Active work preview ──────────────────────────────────────────────────────

@Composable
private fun ActiveWorkSection(
    activeJobs: List<Booking>,
    pendingJobs: List<Booking>,
    onJobClick: (String) -> Unit,
    onStartJob: (String) -> Unit,
    onCompleteJob: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    val total = activeJobs.size + pendingJobs.size
    if (total == 0) return

    Column {
        SectionHeader(
            title = "Việc đang chạy ($total)",
            actionLabel = "Xem tất cả",
            onActionClick = onSeeAll
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Pending completion first (action needed by customer)
            val pendingCompletion = activeJobs.filter { it.status == BookingStatus.PENDING_COMPLETION }
            val inProgress = activeJobs.filter { it.status == BookingStatus.IN_PROGRESS }

            (pendingCompletion + inProgress + pendingJobs).take(2).forEach { booking ->
                when (booking.status) {
                BookingStatus.IN_PROGRESS -> WorkerJobCard(
                        booking = booking,
                        statusColor = StatusBlueProgress,
                        statusLabel = "Đang làm",
                        onClick = { onJobClick(booking.id) },
                        actionLabel = "Báo hoàn thành",
                        actionColor = AccentGreen,
                        onActionClick = { onJobClick(booking.id) }
                    )
                    BookingStatus.PENDING_COMPLETION -> WorkerJobCard(
                        booking = booking,
                        statusColor = StatusOrangeDeep,
                        statusLabel = "Chờ khách xác nhận",
                        onClick = { onJobClick(booking.id) }
                    )
                    BookingStatus.CONFIRMED -> WorkerJobCard(
                        booking = booking,
                        statusColor = StatusOrange,
                        statusLabel = "Đã xác nhận",
                        onClick = { onJobClick(booking.id) },
                        actionLabel = "Bắt đầu",
                        actionColor = PrimaryBlue,
                        onActionClick = { onStartJob(booking.id) }
                    )
                    else -> WorkerJobCard(
                        booking = booking,
                        statusColor = StatusGray,
                        statusLabel = booking.status.name,
                        onClick = { onJobClick(booking.id) }
                    )
                }
            }
        }
    }
}

// ─── Tip banner ───────────────────────────────────────────────────────────────

@Composable
private fun VerifyTipBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Xác minh danh tính",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Tăng tới 30% lượt được chọn bằng việc xác minh hồ sơ",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
