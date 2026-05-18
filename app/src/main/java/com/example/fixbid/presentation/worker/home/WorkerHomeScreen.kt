package com.example.fixbid.presentation.worker.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatShortDateTime
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.presentation.customer.profile.ProfileScreen
import com.example.fixbid.presentation.worker.components.WorkerBottomNavbar
import com.example.fixbid.presentation.worker.jobs.JobRequestsScreen
import com.example.fixbid.ui.theme.*

@Composable
fun WorkerHomeScreen(
    onNotificationClick: () -> Unit = {},
    onJobClick: (String) -> Unit = {},
    onJobRequestClick: (String) -> Unit = {},
    onSignOut: () -> Unit = {},
    showRequestsTab: Boolean = false,
    viewModel: WorkerHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(showRequestsTab) {
        if (showRequestsTab) selectedNavIndex = 1
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            WorkerBottomNavbar(
                selectedIndex = selectedNavIndex,
                onItemSelected = { selectedNavIndex = it }
            )
        },
        containerColor = BackgroundGray,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when (selectedNavIndex) {
            1 -> Box(modifier = Modifier.padding(innerPadding)) {
                JobRequestsScreen(
                    embedded = true,
                    onJobClick = onJobRequestClick
                )
            }
            2 -> Box(modifier = Modifier.padding(innerPadding)) {
                WorkerJobsTab(
                    activeJobs = uiState.activeJobs,
                    pendingJobs = uiState.pendingJobs,
                    isLoading = uiState.isLoading,
                    onJobClick = onJobClick,
                    onStartJob = viewModel::startJob,
                    onCompleteJob = viewModel::completeJob
                )
            }
            3 -> Box(modifier = Modifier.padding(innerPadding)) {
                ProfileScreen(onSignOut = onSignOut)
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    WorkerDashboardHeader(
                        userName = uiState.userName,
                        isAvailable = uiState.isAvailable,
                        isTogglingAvailability = uiState.isTogglingAvailability,
                        onToggleAvailability = viewModel::toggleAvailability,
                        onNotificationClick = onNotificationClick
                    )

                    when {
                        uiState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PrimaryBlue)
                            }
                        }
                        uiState.errorMessage != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        uiState.errorMessage!!,
                                        color = TextSecondary,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = viewModel::loadDashboard) {
                                        Text("Thử lại", color = PrimaryBlue)
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
                                    .padding(
                                        top = 20.dp,
                                        bottom = innerPadding.calculateBottomPadding() + 16.dp
                                    ),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Stats cards
                                StatsRow(
                                    monthlyEarnings = uiState.monthlyEarnings,
                                    completedCount = uiState.completedCount,
                                    rating = uiState.profile?.averageRating ?: 0.0,
                                    totalReviews = uiState.profile?.totalReviews ?: 0
                                )

                                // Quick action: browse open job requests
                                BrowseRequestsCard(
                                    onClick = { selectedNavIndex = 1 }
                                )

                                // Active jobs section
                                if (uiState.activeJobs.isNotEmpty()) {
                                    ActiveJobsSection(
                                        jobs = uiState.activeJobs,
                                        onJobClick = onJobClick,
                                        onCompleteJob = viewModel::completeJob
                                    )
                                }

                                // Pending jobs section
                                if (uiState.pendingJobs.isNotEmpty()) {
                                    PendingJobsSection(
                                        jobs = uiState.pendingJobs,
                                        onJobClick = onJobClick,
                                        onStartJob = viewModel::startJob
                                    )
                                }

                                // Quick stats overview
                                QuickOverviewCard(
                                    totalEarnings = uiState.totalEarnings,
                                    totalJobs = uiState.completedCount,
                                    isVerified = uiState.profile?.identityVerified ?: false
                                )

                                // Empty state
                                if (uiState.activeJobs.isEmpty() && uiState.pendingJobs.isEmpty()) {
                                    EmptyJobsCard()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerDashboardHeader(
    userName: String,
    isAvailable: Boolean,
    isTogglingAvailability: Boolean,
    onToggleAvailability: () -> Unit,
    onNotificationClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PrimaryBlue, Color(0xFF003D61))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Xin chào,",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Text(
                    text = userName.ifEmpty { "Thợ dịch vụ" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            IconButton(onClick = onNotificationClick) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Thông báo",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Availability toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAvailable) AccentGreen else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isAvailable) "Đang sẵn sàng nhận việc" else "Đang tạm nghỉ",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = isAvailable,
                    onCheckedChange = { onToggleAvailability() },
                    enabled = !isTogglingAvailability,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray
                    )
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    monthlyEarnings: Double,
    completedCount: Int,
    rating: Double,
    totalReviews: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.AccountBalanceWallet,
            label = "Thu nhập tháng",
            value = formatCurrency(monthlyEarnings),
            iconTint = Color(0xFF2196F3)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.CheckCircle,
            label = "Hoàn thành",
            value = "$completedCount việc",
            iconTint = AccentGreen
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Star,
            label = "Đánh giá",
            value = if (rating > 0) "%.1f".format(rating) else "—",
            subtitle = if (totalReviews > 0) "($totalReviews)" else null,
            iconTint = Color(0xFFFFA726)
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String? = null,
    iconTint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ActiveJobsSection(
    jobs: List<Booking>,
    onJobClick: (String) -> Unit,
    onCompleteJob: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Đang thực hiện",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
            )
            Badge(
                containerColor = Color(0xFFE3F2FD),
                contentColor = PrimaryBlue
            ) {
                Text(
                    text = "${jobs.size}",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        jobs.forEach { booking ->
            JobCard(
                booking = booking,
                statusColor = Color(0xFF2196F3),
                statusLabel = "Đang làm",
                actionLabel = "Hoàn thành",
                actionColor = AccentGreen,
                onActionClick = { onCompleteJob(booking.id) },
                onClick = { onJobClick(booking.id) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PendingJobsSection(
    jobs: List<Booking>,
    onJobClick: (String) -> Unit,
    onStartJob: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chờ thực hiện",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
            )
            Badge(
                containerColor = Color(0xFFFFF3E0),
                contentColor = Color(0xFFF57C00)
            ) {
                Text(
                    text = "${jobs.size}",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        jobs.forEach { booking ->
            JobCard(
                booking = booking,
                statusColor = Color(0xFFF57C00),
                statusLabel = "Đã xác nhận",
                actionLabel = "Bắt đầu",
                actionColor = PrimaryBlue,
                onActionClick = { onStartJob(booking.id) },
                onClick = { onJobClick(booking.id) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun JobCard(
    booking: Booking,
    statusColor: Color,
    statusLabel: String,
    actionLabel: String,
    actionColor: Color,
    onActionClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
                Text(
                    text = booking.agreedPrice?.let { formatCurrency(it) } ?: "Chưa có giá",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = booking.category.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = booking.description,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatTime(booking.scheduledAt),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun QuickOverviewCard(
    totalEarnings: Double,
    totalJobs: Int,
    isVerified: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tổng quan",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            OverviewRow(
                icon = Icons.Outlined.Payments,
                label = "Tổng thu nhập",
                value = formatCurrency(totalEarnings),
                iconTint = AccentGreen
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = Color(0xFFF5F5F5)
            )
            OverviewRow(
                icon = Icons.Outlined.WorkHistory,
                label = "Tổng công việc",
                value = "$totalJobs việc",
                iconTint = PrimaryBlue
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = Color(0xFFF5F5F5)
            )
            OverviewRow(
                icon = Icons.Outlined.VerifiedUser,
                label = "Xác minh danh tính",
                value = if (isVerified) "Đã xác minh" else "Chưa xác minh",
                iconTint = if (isVerified) AccentGreen else Color(0xFFF57C00)
            )
        }
    }
}

@Composable
private fun OverviewRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

@Composable
private fun EmptyJobsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.WorkOff,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Chưa có công việc nào",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Khi khách hàng đặt dịch vụ, việc làm sẽ hiển thị tại đây",
                fontSize = 13.sp,
                color = TextSecondary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun WorkerJobsTab(
    activeJobs: List<Booking>,
    pendingJobs: List<Booking>,
    isLoading: Boolean,
    onJobClick: (String) -> Unit,
    onStartJob: (String) -> Unit,
    onCompleteJob: (String) -> Unit
) {
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
                text = "Việc làm của tôi",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else if (activeJobs.isEmpty() && pendingJobs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.WorkOff,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Không có việc làm nào",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (activeJobs.isNotEmpty()) {
                    ActiveJobsSection(
                        jobs = activeJobs,
                        onJobClick = onJobClick,
                        onCompleteJob = onCompleteJob
                    )
                }
                if (pendingJobs.isNotEmpty()) {
                    PendingJobsSection(
                        jobs = pendingJobs,
                        onJobClick = onJobClick,
                        onStartJob = onStartJob
                    )
                }
            }
        }
    }
}

// ─── Utility functions ────────────────────────────────────────────────────────
// Use shared formatters from core.utils.FormatUtils

private fun formatCurrency(amount: Double): String = formatCurrencyVnd(amount)
private fun formatTime(timestamp: Long): String = formatShortDateTime(timestamp)


@Composable
private fun BrowseRequestsCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tìm việc mới",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "Duyệt yêu cầu từ khách và đặt giá thầu",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = PrimaryBlue
            )
        }
    }
}
