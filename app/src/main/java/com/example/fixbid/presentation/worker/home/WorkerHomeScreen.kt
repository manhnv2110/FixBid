package com.example.fixbid.presentation.worker.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import com.example.fixbid.core.components.NotificationBell
import com.example.fixbid.core.components.SectionHeader
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatShortDateTime
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.presentation.customer.profile.ProfileScreen
import com.example.fixbid.presentation.worker.components.WorkerBottomNavbar
import com.example.fixbid.presentation.worker.components.WorkerJobCard
import com.example.fixbid.presentation.worker.jobs.JobRequestsScreen
import com.example.fixbid.presentation.worker.jobs.WorkerMyWorkScreen
import com.example.fixbid.ui.theme.*

/**
 * Worker shell with a 4-tab workflow optimised for real-world operation:
 *  0 — Trang chủ (dashboard: today focus, stats, active work, suggestions)
 *  1 — Tìm việc (open job requests — promoted to a first-class tab)
 *  2 — Việc làm (my active / completed work)
 *  3 — Hồ sơ (profile)
 *
 * The earnings card opens a dedicated analytics screen.
 */
@Composable
fun WorkerHomeScreen(
    onNotificationClick: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onJobClick: (String) -> Unit = {},
    onJobRequestClick: (String) -> Unit = {},
    onBrowseAllRequestsClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    showWorkTab: Boolean = false,
    onNotificationSettingsClick: () -> Unit = {},
    viewModel: WorkerHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(showWorkTab) {
        if (showWorkTab) selectedNavIndex = 2
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            WorkerBottomNavbar(
                selectedIndex = selectedNavIndex,
                onItemSelected = { selectedNavIndex = it },
                openRequestCount = uiState.openRequests.size
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when (selectedNavIndex) {
            1 -> Box(modifier = Modifier.padding(innerPadding)) {
                JobRequestsScreen(
                    onBackClick = null,            // embedded as a tab — no back arrow
                    onJobClick = onJobRequestClick
                )
            }
            2 -> Box(modifier = Modifier.padding(innerPadding)) {
                WorkerMyWorkScreen(
                    onJobClick = onJobClick,
                    onStartJob = viewModel::startJob,
                    onCompleteJob = viewModel::completeJob
                )
            }
            3 -> Box(modifier = Modifier.padding(innerPadding)) {
                ProfileScreen(
                    onSignOut = onSignOut,
                    onNotificationSettingsClick = onNotificationSettingsClick
                )
            }
            else -> WorkerDashboard(
                uiState = uiState,
                bottomPadding = innerPadding.calculateBottomPadding(),
                onNotificationClick = onNotificationClick,
                unreadNotificationCount = unreadNotificationCount,
                onToggleAvailability = viewModel::toggleAvailability,
                onRetry = viewModel::loadDashboard,
                onJobClick = onJobClick,
                onJobRequestClick = onJobRequestClick,
                onBrowseAllRequests = { selectedNavIndex = 1 },
                onAnalyticsClick = onAnalyticsClick,
                onSeeAllWork = { selectedNavIndex = 2 },
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
    unreadNotificationCount: Int,
    onToggleAvailability: () -> Unit,
    onRetry: () -> Unit,
    onJobClick: (String) -> Unit,
    onJobRequestClick: (String) -> Unit,
    onBrowseAllRequests: () -> Unit,
    onAnalyticsClick: () -> Unit,
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
            onNotificationClick = onNotificationClick,
            unreadNotificationCount = unreadNotificationCount
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
                    // 1. Today focus — the single most important next action
                    FocusTaskCard(
                        uiState = uiState,
                        onJobClick = onJobClick,
                        onStartJob = onStartJob,
                        onBrowseRequests = onBrowseAllRequests
                    )

                    // 2. Quick stats strip → opens analytics
                    QuickStatsRow(
                        monthlyEarnings = uiState.monthlyEarnings,
                        activeCount = uiState.activeJobs.size + uiState.pendingJobs.size,
                        rating = uiState.profile?.averageRating ?: 0.0,
                        onClick = onAnalyticsClick
                    )

                    // 3. Quick actions
                    QuickActionsRow(
                        onFindJobs = onBrowseAllRequests,
                        onMyWork = onSeeAllWork,
                        onAnalytics = onAnalyticsClick
                    )

                    // 4. Active work
                    ActiveWorkSection(
                        activeJobs = uiState.activeJobs,
                        pendingJobs = uiState.pendingJobs,
                        onJobClick = onJobClick,
                        onStartJob = onStartJob,
                        onSeeAll = onSeeAllWork
                    )

                    // 5. Suggested open requests
                    OpenRequestsSection(
                        requests = uiState.openRequests,
                        onItemClick = onJobRequestClick,
                        onSeeAll = onBrowseAllRequests
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
    onNotificationClick: () -> Unit,
    unreadNotificationCount: Int = 0
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
                    text = greeting(),
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
            NotificationBell(
                unreadCount = unreadNotificationCount,
                onClick = onNotificationClick,
                tint = MaterialTheme.colorScheme.onPrimary
            )
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

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..10 -> "Chào buổi sáng,"
        in 11..13 -> "Chào buổi trưa,"
        in 14..17 -> "Chào buổi chiều,"
        else -> "Chào buổi tối,"
    }
}

// ─── Focus task (next best action) ─────────────────────────────────────────────

@Composable
private fun FocusTaskCard(
    uiState: WorkerHomeUiState,
    onJobClick: (String) -> Unit,
    onStartJob: (String) -> Unit,
    onBrowseRequests: () -> Unit
) {
    val inProgress = uiState.activeJobs.firstOrNull { it.status == BookingStatus.IN_PROGRESS }
    val confirmed = uiState.pendingJobs.firstOrNull { it.status == BookingStatus.CONFIRMED }
    val pendingCompletion = uiState.activeJobs.firstOrNull { it.status == BookingStatus.PENDING_COMPLETION }

    val focus = inProgress ?: confirmed ?: pendingCompletion

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (focus == null) {
            // No active job → nudge to find work
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.WavingHand,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Hôm nay chưa có việc",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tìm yêu cầu mới và đặt thầu để bắt đầu kiếm thu nhập",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onBrowseRequests,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tìm việc ngay", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            val (label, color, actionLabel) = when (focus.status) {
                BookingStatus.IN_PROGRESS -> Triple("Đang thực hiện", StatusBlueProgress, "Mở chi tiết")
                BookingStatus.CONFIRMED -> Triple("Sắp tới • Đã xác nhận", StatusOrange, "Bắt đầu làm")
                else -> Triple("Chờ khách xác nhận", StatusOrangeDeep, "Mở chi tiết")
            }
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.example.fixbid.core.components.StatusPill(text = label, color = color)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Việc cần làm",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = focus.category.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = focus.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                FocusMetaRow(icon = Icons.Outlined.LocationOn, text = focus.address)
                Spacer(Modifier.height(6.dp))
                FocusMetaRow(
                    icon = Icons.Outlined.Schedule,
                    text = "Hẹn ${formatShortDateTime(focus.scheduledAt)} • ${focus.estimatedDurationHours}h"
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Giá trị",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = focus.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Thoả thuận",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = {
                            if (focus.status == BookingStatus.CONFIRMED) onStartJob(focus.id)
                            else onJobClick(focus.id)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(actionLabel, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusMetaRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
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

// ─── Quick stats strip ──────────────────────────────────────────────────────────

@Composable
private fun QuickStatsRow(
    monthlyEarnings: Double,
    activeCount: Int,
    rating: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thống kê nhanh",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Chi tiết",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    modifier = Modifier.weight(1f),
                    value = formatCurrencyVnd(monthlyEarnings),
                    label = "Thu nhập 30 ngày",
                    tint = AccentGreen
                )
                CellDivider()
                StatCell(
                    modifier = Modifier.weight(1f),
                    value = "$activeCount",
                    label = "Việc đang chạy",
                    tint = StatusBlueProgress
                )
                CellDivider()
                StatCell(
                    modifier = Modifier.weight(1f),
                    value = if (rating > 0) "%.1f".format(rating) else "—",
                    label = "Đánh giá",
                    tint = StatusGold
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    tint: Color
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun CellDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

// ─── Quick actions ───────────────────────────────────────────────────────────

@Composable
private fun QuickActionsRow(
    onFindJobs: () -> Unit,
    onMyWork: () -> Unit,
    onAnalytics: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Search,
            label = "Tìm việc",
            onClick = onFindJobs
        )
        QuickAction(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.WorkOutline,
            label = "Việc của tôi",
            onClick = onMyWork
        )
        QuickAction(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.BarChart,
            label = "Thống kê",
            onClick = onAnalytics
        )
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
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
            val pendingCompletion = activeJobs.filter { it.status == BookingStatus.PENDING_COMPLETION }
            val inProgress = activeJobs.filter { it.status == BookingStatus.IN_PROGRESS }

            (inProgress + pendingCompletion + pendingJobs).take(2).forEach { booking ->
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

// ─── Open requests preview ────────────────────────────────────────────────────

@Composable
private fun OpenRequestsSection(
    requests: List<Booking>,
    onItemClick: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    if (requests.isEmpty()) return

    Column {
        SectionHeader(
            title = "Gợi ý cho bạn",
            actionLabel = "Xem tất cả",
            onActionClick = onSeeAll
        )
        Spacer(modifier = Modifier.height(8.dp))
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Text(
                    text = booking.category.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
