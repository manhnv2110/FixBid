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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.fixbid.core.components.AppBottomBar
import com.example.fixbid.core.components.BottomNavDestination
import com.example.fixbid.core.components.ChatBell
import com.example.fixbid.core.components.DraggableAiFab
import com.example.fixbid.core.components.NotificationBell
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatShortDateTime
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.presentation.customer.profile.ProfileScreen
import com.example.fixbid.presentation.worker.components.WorkerJobCard
import com.example.fixbid.presentation.worker.jobs.JobRequestsScreen
import com.example.fixbid.presentation.worker.jobs.WorkerMyWorkScreen
import com.example.fixbid.ui.theme.*

private object WorkerTab {
    const val HOME = "wtab_home"
    const val REQUESTS = "wtab_requests"
    const val WORK = "wtab_work"
    const val PROFILE = "wtab_profile"
    val ordered = listOf(HOME, REQUESTS, WORK, PROFILE)
}

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
    onMyBidsClick: () -> Unit = {},
    onWalletClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onChatbotClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    showWorkTab: Boolean = false,
    onNotificationSettingsClick: () -> Unit = {},
    onWorkerProfileEditClick: () -> Unit = {},
    onVerifyIdentityClick: () -> Unit = {},
    viewModel: WorkerHomeViewModel = hiltViewModel(),
    chatListViewModel: com.example.fixbid.presentation.customer.chat.ConversationListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val chatUnreadCount by chatListViewModel.unreadCount.collectAsState()

    val tabNavController = rememberNavController()
    val currentRoute = tabNavController
        .currentBackStackEntryAsState().value?.destination?.route
        ?: WorkerTab.HOME
    val selectedIndex = WorkerTab.ordered.indexOf(currentRoute).coerceAtLeast(0)

    fun switchTab(route: String) {
        if (route != currentRoute) {
            tabNavController.navigate(route) {
                popUpTo(WorkerTab.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val destinations = listOf(
        BottomNavDestination("Trang chủ", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        BottomNavDestination("Tìm việc", Icons.Filled.Search, Icons.Outlined.Search, badgeCount = uiState.openRequests.size),
        BottomNavDestination("Việc làm", Icons.Filled.Work, Icons.Outlined.WorkOutline),
        BottomNavDestination("Hồ sơ", Icons.Filled.Person, Icons.Outlined.Person)
    )

    LaunchedEffect(showWorkTab) {
        if (showWorkTab) {
            tabNavController.navigate(WorkerTab.WORK) {
                popUpTo(WorkerTab.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AppBottomBar(
                destinations = destinations,
                selectedIndex = selectedIndex,
                onItemSelected = { index -> switchTab(WorkerTab.ordered[index]) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = tabNavController,
                startDestination = WorkerTab.HOME,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(WorkerTab.HOME) {
                    WorkerDashboard(
                        uiState = uiState,
                        bottomPadding = innerPadding.calculateBottomPadding(),
                        onNotificationClick = onNotificationClick,
                        unreadNotificationCount = unreadNotificationCount,
                        chatUnreadCount = chatUnreadCount,
                        onChatClick = onChatClick,
                        onToggleAvailability = viewModel::toggleAvailability,
                        onRetry = viewModel::loadDashboard,
                        onJobClick = onJobClick,
                        onJobRequestClick = onJobRequestClick,
                        onBrowseAllRequests = { switchTab(WorkerTab.REQUESTS) },
                        onAnalyticsClick = onAnalyticsClick,
                        onMyBidsClick = onMyBidsClick,
                        onWalletClick = onWalletClick,
                        onEditProfileClick = onWorkerProfileEditClick,
                        onVerifyIdentityClick = onVerifyIdentityClick,
                        onSeeAllWork = { switchTab(WorkerTab.WORK) },
                        onStartJob = viewModel::startJob,
                        onCompleteJob = viewModel::completeJob,
                        onAcceptDirect = viewModel::acceptDirectBooking,
                        onDeclineDirect = viewModel::declineDirectBooking
                    )
                }
                composable(WorkerTab.REQUESTS) {
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        JobRequestsScreen(
                            onBackClick = null,            // embedded as a tab — no back arrow
                            onJobClick = onJobRequestClick
                        )
                    }
                }
                composable(WorkerTab.WORK) {
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        WorkerMyWorkScreen(
                            onJobClick = onJobClick,
                            onStartJob = viewModel::startJob,
                            onCompleteJob = viewModel::completeJob
                        )
                    }
                }
                composable(WorkerTab.PROFILE) {
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        ProfileScreen(
                            onSignOut = onSignOut,
                            onNotificationSettingsClick = onNotificationSettingsClick,
                            onWorkerProfileClick = onWorkerProfileEditClick
                        )
                    }
                }
            }

            // Draggable AI assistant overlay — only on the dashboard tab.
            if (currentRoute == WorkerTab.HOME) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    DraggableAiFab(
                        onClick = onChatbotClick,
                        storageKey = "worker_ai_fab"
                    )
                }
            }
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
    chatUnreadCount: Int,
    onChatClick: () -> Unit,
    onToggleAvailability: () -> Unit,
    onRetry: () -> Unit,
    onJobClick: (String) -> Unit,
    onJobRequestClick: (String) -> Unit,
    onBrowseAllRequests: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onMyBidsClick: () -> Unit,
    onWalletClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onVerifyIdentityClick: () -> Unit,
    onSeeAllWork: () -> Unit,
    onStartJob: (String) -> Unit,
    onCompleteJob: (String) -> Unit,
    onAcceptDirect: (String) -> Unit,
    onDeclineDirect: (bookingId: String, reason: String) -> Unit
) {
    // Local UI state for the decline-reason dialog. The viewmodel already
    // exposes `respondingDirectId` for the in-flight indicator on the card,
    // so this composable only needs to track which booking is being asked
    // about (held in process memory; recreated cheaply on rotation).
    var declineTarget by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        DashboardHeader(
            userName = uiState.userName,
            isAvailable = uiState.isAvailable,
            isToggling = uiState.isTogglingAvailability,
            onToggleAvailability = onToggleAvailability,
            onNotificationClick = onNotificationClick,
            unreadNotificationCount = unreadNotificationCount,
            chatUnreadCount = chatUnreadCount,
            onChatClick = onChatClick
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
                // The single "next action" job — shown in the hero focus card and
                // excluded from the list below so the same job never appears twice.
                val focusJob = uiState.activeJobs.firstOrNull { it.status == BookingStatus.IN_PROGRESS }
                    ?: uiState.pendingJobs.firstOrNull { it.status == BookingStatus.CONFIRMED }
                    ?: uiState.activeJobs.firstOrNull { it.status == BookingStatus.PENDING_COMPLETION }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = bottomPadding + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Earnings hero — primary KPI for a service provider
                    EarningsHeroCard(
                        monthlyEarnings = uiState.monthlyEarnings,
                        completedCount = uiState.completedCount,
                        rating = uiState.profile?.averageRating ?: 0.0,
                        totalReviews = uiState.profile?.totalReviews ?: 0,
                        onClick = onAnalyticsClick
                    )

                    // 2. Stats tile row (active + pending bids)
                    DashboardStatsRow(
                        activeCount = uiState.activeJobs.size + uiState.pendingJobs.size,
                        openRequestCount = uiState.openRequests.size,
                        onActiveClick = onSeeAllWork,
                        onRequestsClick = onBrowseAllRequests
                    )

                    // 3. Today focus — the single most important next action
                    DashboardSectionTitle(title = "Việc cần làm hôm nay")
                    FocusTaskCard(
                        focus = focusJob,
                        onJobClick = onJobClick,
                        onStartJob = onStartJob,
                        onBrowseRequests = onBrowseAllRequests
                    )

                    // 3b. Direct booking requests waiting for accept/decline.
                    //     Surfaced right under the focus card because they're
                    //     time-sensitive: the customer is sitting on the booking
                    //     screen waiting to know whether to pay you or move on.
                    if (uiState.pendingDirectRequests.isNotEmpty()) {
                        DashboardSectionTitle(
                            title = "Yêu cầu trực tiếp (${uiState.pendingDirectRequests.size})"
                        )
                        PendingDirectRequestsSection(
                            requests = uiState.pendingDirectRequests,
                            respondingId = uiState.respondingDirectId,
                            onItemClick = onJobClick,
                            onAccept = onAcceptDirect,
                            onDecline = { bookingId -> declineTarget = bookingId }
                        )
                    }

                    // 4. Secondary shortcuts
                    DashboardSectionTitle(title = "Lối tắt")
                    SecondaryShortcuts(
                        pendingBidCount = uiState.openRequests.size,
                        needsProfileSetup = uiState.profile?.skills.isNullOrEmpty(),
                        onMyBids = onMyBidsClick,
                        onWallet = onWalletClick,
                        onEditProfile = onEditProfileClick
                    )

                    // 5. Other active work (excluding the focus job above)
                    ActiveWorkSection(
                        activeJobs = uiState.activeJobs,
                        pendingJobs = uiState.pendingJobs,
                        excludeId = focusJob?.id,
                        onJobClick = onJobClick,
                        onStartJob = onStartJob,
                        onSeeAll = onSeeAllWork
                    )

                    // 6. Suggested open requests
                    OpenRequestsSection(
                        requests = uiState.openRequests,
                        onItemClick = onJobRequestClick,
                        onSeeAll = onBrowseAllRequests
                    )

                    if (uiState.profile?.identityVerified == false) {
                        VerifyTipBanner(onVerifyClick = onVerifyIdentityClick)
                    }
                }
            }
        }
    }

    // Decline-reason dialog — opened from a Direct request card. We only
    // show it after the worker explicitly chose to decline a specific booking
    // so the dashboard stays uncluttered the rest of the time.
    declineTarget?.let { bookingId ->
        DeclineReasonDialog(
            isSubmitting = uiState.respondingDirectId == bookingId,
            onDismiss = { declineTarget = null },
            onConfirm = { reason ->
                onDeclineDirect(bookingId, reason)
                declineTarget = null
            }
        )
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
    unreadNotificationCount: Int = 0,
    chatUnreadCount: Int = 0,
    onChatClick: () -> Unit = {}
) {
    // Single-block primary header with two stacked rows: identity/actions on
    // top, availability toggle below as a translucent inset bar that lives
    // *inside* the header so it can't be covered when the page is scrolled.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 16.dp)
        ) {
            // Row 1 — identity + bells
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (userName.trim().firstOrNull()?.uppercase() ?: "T"),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greeting(),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = userName.ifEmpty { "Thợ dịch vụ" },
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChatBell(
                        unreadCount = chatUnreadCount,
                        onClick = onChatClick,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    NotificationBell(
                        unreadCount = unreadNotificationCount,
                        onClick = onNotificationClick,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 2 — availability bar (kept inside the header)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAvailable) AccentGreen.copy(alpha = 0.28f)
                                else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(if (isAvailable) AccentGreen else StatusColorsTheme.current.neutral)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAvailable) "Đang sẵn sàng nhận việc"
                            else "Đang tạm nghỉ",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isAvailable) "Khách hàng có thể tìm thấy bạn"
                            else "Bật để bắt đầu nhận yêu cầu",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = isAvailable,
                        onCheckedChange = { onToggleAvailability() },
                        enabled = !isToggling,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = AccentGreen,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.32f)
                        )
                    )
                }
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
    focus: Booking?,
    onJobClick: (String) -> Unit,
    onStartJob: (String) -> Unit,
    onBrowseRequests: () -> Unit
) {
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
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.WavingHand,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
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
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tìm việc ngay", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            val sc = StatusColorsTheme.current
            val (label, color, actionLabel) = when (focus.status) {
                BookingStatus.IN_PROGRESS -> Triple("Đang thực hiện", sc.inProgress, "Mở chi tiết")
                BookingStatus.CONFIRMED -> Triple("Sắp tới • Đã xác nhận", sc.awaitingPayment, "Bắt đầu làm")
                else -> Triple("Chờ khách xác nhận", sc.pendingCompletion, "Mở chi tiết")
            }
            // Status-coloured left accent stripe so the urgency reads at a glance
            // even before the user processes the text in the pill.
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(color)
                )
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.fixbid.core.components.StatusPill(text = label, color = color)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = focus.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Thoả thuận",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = color
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
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    FocusMetaRow(icon = Icons.Outlined.LocationOn, text = focus.address)
                    Spacer(Modifier.height(6.dp))
                    FocusMetaRow(
                        icon = Icons.Outlined.Schedule,
                        text = "Hẹn ${formatShortDateTime(focus.scheduledAt)} • ${focus.estimatedDurationHours}h"
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (focus.status == BookingStatus.CONFIRMED) onStartJob(focus.id)
                            else onJobClick(focus.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        contentPadding = PaddingValues(vertical = 12.dp)
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

// ─── Section title (with leading accent stripe) ──────────────────────────────

@Composable
private fun DashboardSectionTitle(title: String) {
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
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Section title with a "see all" action on the trailing edge. */
@Composable
private fun DashboardSectionRow(
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TextButton(
            onClick = onActionClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = actionLabel,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─── Earnings hero card ───────────────────────────────────────────────────────

/**
 * Big earnings card at the top of the worker dashboard. Earnings is the most
 * important metric for a service provider, so it gets a primary-coloured hero
 * treatment with secondary stats (jobs done + rating) anchored to the bottom.
 *
 * Tapping the card jumps to the dedicated analytics screen, matching the
 * affordance suggested by the trailing chevron.
 */
@Composable
private fun EarningsHeroCard(
    monthlyEarnings: Double,
    completedCount: Int,
    rating: Double,
    totalReviews: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Wallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Thu nhập 30 ngày",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatCurrencyVnd(monthlyEarnings),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Chi tiết",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-metrics inside a translucent strip so they read clearly on
            // the primary fill without fighting the headline number.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroSubMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.WorkOutline,
                        label = "Việc đã làm",
                        value = "$completedCount"
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                    )
                    HeroSubMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Star,
                        label = if (totalReviews > 0) "$totalReviews đánh giá" else "Đánh giá",
                        value = if (rating > 0) "%.1f".format(rating) else "—"
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSubMetric(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = modifier.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Stats row (active jobs + open requests) ──────────────────────────────────

@Composable
private fun DashboardStatsRow(
    activeCount: Int,
    openRequestCount: Int,
    onActiveClick: () -> Unit,
    onRequestsClick: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.PendingActions,
            value = "$activeCount",
            label = "Đang xử lý",
            tint = StatusColorsTheme.current.inProgress,
            onClick = onActiveClick
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.NewReleases,
            value = "$openRequestCount",
            label = "Yêu cầu mới",
            tint = StatusColorsTheme.current.pendingCompletion,
            onClick = onRequestsClick
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
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

// ─── Secondary shortcuts (only destinations not already in the bottom nav) ─────

@Composable
private fun SecondaryShortcuts(
    pendingBidCount: Int,
    needsProfileSetup: Boolean,
    onMyBids: () -> Unit,
    onWallet: () -> Unit,
    onEditProfile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.AccountBalanceWallet,
                title = "Ví của tôi",
                subtitle = "Số dư & lịch sử thanh toán",
                onClick = onWallet
            )
            ShortcutCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Gavel,
                title = "Báo giá của tôi",
                subtitle = "Theo dõi báo giá đã gửi",
                onClick = onMyBids
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.ManageAccounts,
                title = "Hồ sơ nghề nghiệp",
                subtitle = if (needsProfileSetup) "Hoàn thiện để nhận việc" else "Kỹ năng, giá, kinh nghiệm",
                highlight = needsProfileSetup,
                onClick = onEditProfile
            )
            // Spacer so the row keeps the same column widths even with one
            // visible tile, keeping the dashboard rhythm consistent.
            Box(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ShortcutCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val accentColor = if (highlight) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (highlight) MaterialTheme.colorScheme.primary
                            else accentColor.copy(alpha = 0.14f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (highlight) MaterialTheme.colorScheme.onPrimary
                        else accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (highlight) {
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Mới",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                lineHeight = 14.sp
            )
        }
    }
}

// ─── Active work preview ──────────────────────────────────────────────────────

@Composable
private fun ActiveWorkSection(
    activeJobs: List<Booking>,
    pendingJobs: List<Booking>,
    excludeId: String? = null,
    onJobClick: (String) -> Unit,
    onStartJob: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    val totalAll = activeJobs.size + pendingJobs.size
    val remaining = (activeJobs + pendingJobs).filter { it.id != excludeId }
    if (remaining.isEmpty()) return

    Column {
        DashboardSectionRow(
            title = "Việc đang chạy ($totalAll)",
            actionLabel = "Xem tất cả",
            onActionClick = onSeeAll
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val pendingCompletion = remaining.filter { it.status == BookingStatus.PENDING_COMPLETION }
            val inProgress = remaining.filter { it.status == BookingStatus.IN_PROGRESS }
            val confirmed = remaining.filter { it.status == BookingStatus.CONFIRMED }

            (inProgress + pendingCompletion + confirmed).take(3).forEach { booking ->
                when (booking.status) {
                    BookingStatus.IN_PROGRESS -> WorkerJobCard(
                        booking = booking,
                        statusColor = statusColor(BookingStatus.IN_PROGRESS),
                        statusLabel = "Đang làm",
                        onClick = { onJobClick(booking.id) },
                        actionLabel = "Báo hoàn thành",
                        actionColor = AccentGreen,
                        onActionClick = { onJobClick(booking.id) }
                    )
                    BookingStatus.PENDING_COMPLETION -> WorkerJobCard(
                        booking = booking,
                        statusColor = statusColor(BookingStatus.PENDING_COMPLETION),
                        statusLabel = "Chờ khách xác nhận",
                        onClick = { onJobClick(booking.id) }
                    )
                    BookingStatus.CONFIRMED -> WorkerJobCard(
                        booking = booking,
                        statusColor = statusColor(BookingStatus.CONFIRMED),
                        statusLabel = "Đã xác nhận",
                        onClick = { onJobClick(booking.id) },
                        actionLabel = "Bắt đầu",
                        actionColor = MaterialTheme.colorScheme.primary,
                        onActionClick = { onStartJob(booking.id) }
                    )
                    else -> WorkerJobCard(
                        booking = booking,
                        statusColor = statusColor(booking.status),
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
        DashboardSectionRow(
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
private fun VerifyTipBanner(onVerifyClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Xác minh danh tính",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Tăng tới 30% lượt được chọn khi xác minh",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onVerifyClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Xác minh",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}


// ─── Pending direct booking requests ─────────────────────────────────────────

/**
 * Section that surfaces the worker's [pendingDirectRequests] — DIRECT-type
 * bookings the customer assigned to them that are still PENDING. Each card
 * shows enough context for an instant decision (category, scheduled time,
 * budget, address) and exposes Accept / Decline actions inline so the worker
 * doesn't need to drill into the detail screen for routine cases.
 */
@Composable
private fun PendingDirectRequestsSection(
    requests: List<Booking>,
    respondingId: String?,
    onItemClick: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Take the 3 most-recent requests for the dashboard. The full list lives
        // on the dedicated requests tab; the worker can see all from there.
        requests.take(3).forEach { booking ->
            DirectRequestCard(
                booking = booking,
                isResponding = respondingId == booking.id,
                onClick = { onItemClick(booking.id) },
                onAccept = { onAccept(booking.id) },
                onDecline = { onDecline(booking.id) }
            )
        }
    }
}

@Composable
private fun DirectRequestCard(
    booking: Booking,
    isResponding: Boolean,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val accent = StatusColorsTheme.current.pending
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isResponding, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Status accent stripe so the row reads at a glance.
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(Modifier.padding(16.dp)) {
                // Header row — pill + customer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.example.fixbid.core.components.StatusPill(
                        text = "Đặt trực tiếp",
                        color = accent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = booking.customer?.fullName ?: "Khách hàng",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = booking.category.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (booking.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = booking.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                Spacer(Modifier.height(10.dp))
                FocusMetaRow(icon = Icons.Outlined.LocationOn, text = booking.address)
                Spacer(Modifier.height(4.dp))
                FocusMetaRow(
                    icon = Icons.Outlined.Schedule,
                    text = "Hẹn ${formatShortDateTime(booking.scheduledAt)} • ${booking.estimatedDurationHours}h"
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        enabled = !isResponding,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Từ chối", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Button(
                        onClick = onAccept,
                        enabled = !isResponding,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        if (isResponding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Nhận đơn", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reason capture for declining a direct booking. Mirrors the screen-level
 * dialog in JobDetailScreen so the worker has a consistent decline flow whether
 * they tap it from the dashboard card or from the detail screen.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DeclineReasonDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val presetReasons = listOf(
        "Không trống lịch hôm đó",
        "Vị trí quá xa",
        "Không đúng chuyên môn",
        "Đang bận đơn khác"
    )
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Từ chối đơn?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Cho khách biết lý do để họ tìm thợ khác phù hợp hơn:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetReasons.forEach { preset ->
                        FilterChip(
                            selected = reason == preset,
                            onClick = { reason = preset },
                            label = { Text(preset, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("Hoặc nhập lý do khác...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isSubmitting
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = { onConfirm(reason) }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "Xác nhận từ chối",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text("Huỷ") }
        }
    )
}
