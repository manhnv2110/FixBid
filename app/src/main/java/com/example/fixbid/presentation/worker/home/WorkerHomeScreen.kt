package com.example.fixbid.presentation.worker.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.fixbid.core.components.AppBottomBar
import com.example.fixbid.core.components.BottomNavDestination
import com.example.fixbid.core.components.ChatBell
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
    onChatbotPrefill: (String) -> Unit = {},
    onSignOut: () -> Unit = {},
    showWorkTab: Boolean = false,
    onNotificationSettingsClick: () -> Unit = {},
    onWorkerProfileEditClick: () -> Unit = {},
    onVerifyIdentityClick: () -> Unit = {},
    onHelpSupportClick: () -> Unit = {},
    viewModel: WorkerHomeViewModel = hiltViewModel(),
    chatListViewModel: com.example.fixbid.presentation.customer.chat.ConversationListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val chatUnreadCount by chatListViewModel.unreadCount.collectAsState()

    // Refresh the dashboard whenever this shell re-enters the foreground —
    // covers the case where the worker cancels / starts / completes a job in
    // a child screen (JobDetail, etc.) and pops back here. Without this, the
    // job cards keep showing stale buttons like "Bắt đầu làm" until the user
    // pulls to refresh manually.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadDashboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tabNavController = rememberNavController()
    val currentRoute = tabNavController
        .currentBackStackEntryAsState().value?.destination?.route
        ?: WorkerTab.HOME
    val selectedIndex = WorkerTab.ordered.indexOf(currentRoute).coerceAtLeast(0)

    LaunchedEffect(currentRoute) {
        if (currentRoute == WorkerTab.HOME) {
            viewModel.loadDashboard()
        }
    }

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
                    // ── AI shortcut state for the dashboard ──────────────
                    val aiInlineState by viewModel.aiController.inlineState.collectAsState()
                    val aiPendingPrefill by viewModel.aiController.pendingChatPrefill.collectAsState()
                    LaunchedEffect(aiPendingPrefill) {
                        aiPendingPrefill?.let {
                            onChatbotPrefill(it)
                            viewModel.aiController.consumeChatPrefill()
                        }
                    }
                    val aiSuggestions = remember(
                        uiState.pendingDirectRequests.size,
                        uiState.openRequests.size,
                        uiState.activeJobs.size
                    ) { viewModel.aiSuggestions() }

                    WorkerDashboard(
                        uiState = uiState,
                        bottomPadding = innerPadding.calculateBottomPadding(),
                        onNotificationClick = onNotificationClick,
                        unreadNotificationCount = unreadNotificationCount,
                        chatUnreadCount = chatUnreadCount,
                        onChatClick = onChatClick,
                        onToggleAvailability = viewModel::toggleAvailability,
                        onRetry = { viewModel.loadDashboard(forceShowLoading = true) },
                        onRefresh = viewModel::refresh,
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
                        onSendQuote = viewModel::quoteDirectBooking,
                        onDeclineDirect = viewModel::declineDirectBooking,
                        aiSuggestions = aiSuggestions,
                        aiInlineState = aiInlineState,
                        onAiSuggestionClick = { viewModel.aiController.onSuggestionTapped(it) },
                        onAiInlineRetry = { viewModel.aiController.retryInline() },
                        onAiInlineDismiss = { viewModel.aiController.dismissInline() },
                        onAiInlineOpenChat = { viewModel.aiController.openInlineInChat() }
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
                            onWorkerProfileClick = onWorkerProfileEditClick,
                            onHelpSupportClick = onHelpSupportClick
                        )
                    }
                }
            }

            // The AI assistant FAB is now hosted globally by `FixBidNavHost`,
            // so it can travel across every worker screen (jobs, wallet, profile…)
            // instead of being pinned to the dashboard tab.
        }
    }
}

// ─── Dashboard ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
    onRefresh: () -> Unit,
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
    onSendQuote: (bookingId: String, price: Double, message: String, durationHours: Double?) -> Unit,
    onDeclineDirect: (bookingId: String, reason: String) -> Unit,
    aiSuggestions: List<com.example.fixbid.domain.model.AiSuggestion> = emptyList(),
    aiInlineState: com.example.fixbid.presentation.ai.InlineAiState =
        com.example.fixbid.presentation.ai.InlineAiState.Idle,
    onAiSuggestionClick: (com.example.fixbid.domain.model.AiSuggestion) -> Unit = {},
    onAiInlineRetry: () -> Unit = {},
    onAiInlineDismiss: () -> Unit = {},
    onAiInlineOpenChat: () -> Unit = {}
) {
    // Local UI state for the decline-reason dialog. The viewmodel already
    // exposes `respondingDirectId` for the in-flight indicator on the card,
    // so this composable only needs to track which booking is being asked
    // about (held in process memory; recreated cheaply on rotation).
    var declineTarget by remember { mutableStateOf<String?>(null) }
    // Same pattern for the quote dialog: the dashboard owns which booking
    // is currently being quoted; the dialog handles the form.
    var quoteTarget by remember { mutableStateOf<Booking?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        DashboardHeader(
            userName = uiState.userName,
            avatarUrl = uiState.avatarUrl,
            isAvailable = uiState.isAvailable,
            isToggling = uiState.isTogglingAvailability,
            onToggleAvailability = onToggleAvailability,
            onNotificationClick = onNotificationClick,
            unreadNotificationCount = unreadNotificationCount,
            chatUnreadCount = chatUnreadCount,
            onChatClick = onChatClick
        )

        when {
            uiState.isLoading && uiState.profile == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onRetry) { Text("Thử lại") }
                    }
                }
            }
            else -> {
                // The single "next action" job — shown in the focus card and
                // excluded from the list below so the same job never appears twice.
                val focusJob = uiState.activeJobs.firstOrNull { it.status == BookingStatus.IN_PROGRESS }
                    ?: uiState.pendingJobs.firstOrNull { it.status == BookingStatus.CONFIRMED }
                    ?: uiState.activeJobs.firstOrNull { it.status == BookingStatus.PENDING_COMPLETION }

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = bottomPadding + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 0. AI shortcuts — top of the scroll so the worker
                        // sees suggestions (price recommendation, summary,
                        // analytics insights) right after the header.
                        com.example.fixbid.presentation.ai.AiSuggestionStrip(
                            suggestions = aiSuggestions,
                            onSuggestionClick = onAiSuggestionClick
                        )
                        com.example.fixbid.presentation.ai.InlineAiAnalysisCard(
                            state = aiInlineState,
                            onRetry = onAiInlineRetry,
                            onDismiss = onAiInlineDismiss,
                            onOpenChat = onAiInlineOpenChat
                        )

                        // 1. Earnings summary — slim entry-point to analytics.
                        EarningsSummaryCard(
                            monthlyEarnings = uiState.monthlyEarnings,
                            completedCount = uiState.completedCount,
                            rating = uiState.profile?.averageRating ?: 0.0,
                            totalReviews = uiState.profile?.totalReviews ?: 0,
                            onClick = onAnalyticsClick
                        )

                        // 2. Quick actions — tonal chip strip.
                        QuickActionsRow(
                            openRequestCount = uiState.openRequests.size,
                            onFindWork = onBrowseAllRequests,
                            onMyBids = onMyBidsClick,
                            onWallet = onWalletClick
                        )

                        // 3. Direct booking requests — surfaced first because
                        //    they're time-sensitive (customer is waiting).
                        if (uiState.pendingDirectRequests.isNotEmpty()) {
                            DashboardSection(
                                title = "Yêu cầu trực tiếp",
                                trailingCount = uiState.pendingDirectRequests.size
                            ) {
                                PendingDirectRequestsSection(
                                    requests = uiState.pendingDirectRequests,
                                    respondingId = uiState.respondingDirectId,
                                    onItemClick = onJobClick,
                                    onSendQuote = { booking -> quoteTarget = booking },
                                    onDecline = { bookingId -> declineTarget = bookingId }
                                )
                            }
                        }

                        // 4. Today focus — the single most important next action.
                        DashboardSection(title = "Việc cần làm hôm nay") {
                            FocusTaskCard(
                                focus = focusJob,
                                onJobClick = onJobClick,
                                onStartJob = onStartJob,
                                onBrowseRequests = onBrowseAllRequests
                            )
                        }

                        // 5. Other active work (excluding the focus job above).
                        ActiveWorkSection(
                            activeJobs = uiState.activeJobs,
                            pendingJobs = uiState.pendingJobs,
                            excludeId = focusJob?.id,
                            onJobClick = onJobClick,
                            onStartJob = onStartJob,
                            onSeeAll = onSeeAllWork
                        )

                        // 6. Suggested open requests.
                        OpenRequestsSection(
                            requests = uiState.openRequests,
                            onItemClick = onJobRequestClick,
                            onSeeAll = onBrowseAllRequests
                        )

                        // 7. Contextual nudges — only shown when actionable.
                        if (uiState.profile?.skills.isNullOrEmpty()) {
                            ProfileSetupBanner(onClick = onEditProfileClick)
                        }
                        if (uiState.profile?.identityVerified == false) {
                            VerifyTipBanner(onVerifyClick = onVerifyIdentityClick)
                        }
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

    // Quote dialog — opened from a Direct request card when the worker taps
    // "Báo giá". The dialog collects price + duration + message and submits
    // through `onSendQuote`. We close it on successful submission so the worker
    // sees the dashboard reload with the new "đã báo giá" state.
    quoteTarget?.let { booking ->
        QuoteDirectFromDashboardDialog(
            booking = booking,
            isSubmitting = uiState.respondingDirectId == booking.id,
            onDismiss = { quoteTarget = null },
            onConfirm = { price, durationHours, message ->
                onSendQuote(booking.id, price, message, durationHours)
                quoteTarget = null
            }
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    userName: String,
    avatarUrl: String? = null,
    isAvailable: Boolean,
    isToggling: Boolean,
    onToggleAvailability: () -> Unit,
    onNotificationClick: () -> Unit,
    unreadNotificationCount: Int = 0,
    chatUnreadCount: Int = 0,
    onChatClick: () -> Unit = {}
) {
    // Compact, content-first header. The old design dedicated an entire inset
    // bar to the availability switch — visually heavy and pushing all real
    // content below the fold. This refactor keeps everything in a single row:
    //   avatar + greeting/name (with a small status dot)  ·  bells  ·  toggle
    // The availability state is now read at-a-glance via the dot beside the
    // name, and the user can flip it with the trailing icon button without
    // surrendering 80dp+ of vertical space.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                            else -> AvatarInitial(userName)
                        }
                    }
                } else {
                    AvatarInitial(userName)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Greeting + name + availability chip
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
                Text(
                    text = userName.ifEmpty { "Thợ dịch vụ" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                AvailabilityChip(
                    isAvailable = isAvailable,
                    isToggling = isToggling,
                    onClick = onToggleAvailability
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

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
}

@Composable
private fun AvatarInitial(userName: String) {
    Text(
        text = userName.trim().firstOrNull()?.uppercase() ?: "T",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary
    )
}

/**
 * Inline availability indicator + toggle. A pill that shows current state and
 * flips it on click. Replaces the heavy "inset bar with switch" design.
 */
@Composable
private fun AvailabilityChip(
    isAvailable: Boolean,
    isToggling: Boolean,
    onClick: () -> Unit
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val bg = if (isAvailable) AccentGreen.copy(alpha = 0.22f) else onPrimary.copy(alpha = 0.16f)
    val dot = if (isAvailable) AccentGreen else StatusColorsTheme.current.neutral
    Surface(
        modifier = Modifier.clickable(enabled = !isToggling, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isAvailable) "Sẵn sàng nhận việc" else "Đang tạm nghỉ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = onPrimary
            )
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
    if (focus == null) {
        // Empty state — clean tonal card with a single CTA.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Tìm yêu cầu mới và đặt thầu để bắt đầu kiếm thu nhập",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onBrowseRequests,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tìm việc ngay", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        return
    }

    val sc = StatusColorsTheme.current
    val (statusLabel, statusColor, actionLabel) = when (focus.status) {
        BookingStatus.IN_PROGRESS -> Triple("Đang thực hiện", sc.inProgress, "Mở chi tiết")
        BookingStatus.CONFIRMED -> Triple("Đã xác nhận", sc.awaitingPayment, "Bắt đầu làm")
        else -> Triple("Chờ khách xác nhận", sc.pendingCompletion, "Mở chi tiết")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onJobClick(focus.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Top row: status pill · price
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                com.example.fixbid.core.components.StatusPill(text = statusLabel, color = statusColor)
                Spacer(Modifier.weight(1f))
                Text(
                    text = focus.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Thoả thuận",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = focus.category.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (focus.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = focus.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

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
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Section header ──────────────────────────────────────────────────────────

/**
 * Modern, clean section header used across the dashboard.
 *
 * Replaces the older accent-stripe + bold-Text combo with a flat M3-style title
 * (typography titleMedium SemiBold) and an optional trailing action button. A
 * compact tonal count chip can be shown next to the title for sections with a
 * known item count (e.g. "Yêu cầu trực tiếp · 3"). The body slot keeps the
 * title visually anchored to its content with a tight 12dp spacing.
 */
@Composable
private fun DashboardSection(
    title: String,
    trailingCount: Int? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (trailingCount != null && trailingCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "$trailingCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (actionLabel != null && onActionClick != null) {
                TextButton(
                    onClick = onActionClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
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
        }
        content()
    }
}

// ─── Earnings summary card ────────────────────────────────────────────────────

/**
 * Slim earnings entry-point — the headline KPI for a service provider. Uses
 * `primaryContainer` matching the app's existing hero-card convention
 * (PromoBanner, FindWorkersCta on the customer home), so the worker dashboard
 * speaks the same visual language as the rest of the product.
 *
 * Tap opens analytics — the only path to that screen from the dashboard.
 */
@Composable
private fun EarningsSummaryCard(
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                    Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Thu nhập 30 ngày",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                )
                Text(
                    text = formatCurrencyVnd(monthlyEarnings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = earningsCaption(completedCount, rating, totalReviews),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Chi tiết phân tích",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** "12 việc · 4.8 ★ (37)" / "Chưa có dữ liệu" — keeps the slim card readable. */
private fun earningsCaption(completedCount: Int, rating: Double, totalReviews: Int): String {
    if (completedCount == 0 && rating <= 0) return "Chưa có dữ liệu — bấm để xem chi tiết"
    val parts = buildList {
        if (completedCount > 0) add("$completedCount việc")
        if (rating > 0) {
            val rounded = "%.1f".format(rating)
            add(if (totalReviews > 0) "$rounded ★ ($totalReviews)" else "$rounded ★")
        }
    }
    return parts.joinToString(" · ")
}

// ─── Quick actions ────────────────────────────────────────────────────────────

/**
 * A single compact row of primary shortcuts, replacing the old stat tiles +
 * 2×2 shortcut grid. Each action is an icon-in-a-tonal-circle with a short
 * label below — the same visual language as the customer home category grid,
 * so the two halves of the app feel like one product.
 *
 * "Tìm việc" carries a count badge for new open requests, folding the old
 * "Yêu cầu mới" stat tile into an actionable destination instead of a
 * read-only number that duplicated the bottom-nav badge.
 */
// ─── Quick actions ────────────────────────────────────────────────────────────

/**
 * Three primary shortcuts grouped inside a tonal container card. Uses
 * `secondaryContainer` — same blue family as the Earnings card's
 * `primaryContainer` but a lighter shade per the app's palette — to express a
 * clear "primary → secondary" hierarchy on the dashboard while staying inside
 * the brand color system the rest of the app already speaks (PromoBanner,
 * FindWorkersCta, NotificationBell, etc. all use the primary/secondary
 * container tokens).
 *
 * Each action's icon sits in a `surface` circle so the icons retain their
 * brand-blue tint against the lighter container without the whole row feeling
 * like one flat blob.
 */
@Composable
private fun QuickActionsRow(
    openRequestCount: Int,
    onFindWork: () -> Unit,
    onMyBids: () -> Unit,
    onWallet: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            QuickAction(
                icon = Icons.Outlined.Search,
                label = "Tìm việc",
                badgeCount = openRequestCount,
                onClick = onFindWork
            )
            QuickAction(
                icon = Icons.Outlined.Gavel,
                label = "Báo giá",
                onClick = onMyBids
            )
            QuickAction(
                icon = Icons.Outlined.AccountBalanceWallet,
                label = "Ví",
                onClick = onWallet
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge {
                        Text(
                            text = if (badgeCount > 9) "9+" else "$badgeCount",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        ) {
            // Icon sits in a `surface` (white) circle so it pops against the
            // lighter secondaryContainer wrapper, while keeping the brand-blue
            // primary tint for the icon itself — same role pattern customer
            // home uses for its category tiles.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1
        )
    }
}

// ─── Profile setup banner ───────────────────────────────────────────────────

/**
 * Nudge to finish the professional profile. Only shown while the worker has no
 * skills configured (they can't be matched to jobs without them), so it
 * disappears from the dashboard once setup is complete.
 */
@Composable
private fun ProfileSetupBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.ManageAccounts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hoàn thiện hồ sơ nghề nghiệp",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Thêm kỹ năng & giá để bắt đầu nhận việc",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
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

    DashboardSection(
        title = "Việc đang chạy",
        trailingCount = totalAll,
        actionLabel = "Tất cả",
        onActionClick = onSeeAll
    ) {
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

    DashboardSection(
        title = "Gợi ý cho bạn",
        actionLabel = "Tất cả",
        onActionClick = onSeeAll
    ) {
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = booking.category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = booking.description,
                    style = MaterialTheme.typography.bodySmall,
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
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = booking.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = booking.agreedPrice?.let { formatCurrencyVnd(it) } ?: "Thoả thuận",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ─── Tip banner ───────────────────────────────────────────────────────────────

@Composable
private fun VerifyTipBanner(onVerifyClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onVerifyClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Tăng tới 30% lượt được chọn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
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
    onSendQuote: (Booking) -> Unit,
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
                onSendQuote = { onSendQuote(booking) },
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
    onSendQuote: () -> Unit,
    onDecline: () -> Unit
) {
    // Direct bookings show up in two flavours on this card: PENDING means we
    // still owe the customer a quote, QUOTED means we already sent one and are
    // waiting on their response. The card adapts the pill, accent colour and
    // CTA copy so the worker sees at a glance which stage they're in.
    val isQuoted = booking.status == BookingStatus.QUOTED
    val accent = if (isQuoted) StatusColorsTheme.current.quoted else StatusColorsTheme.current.pending
    val pillText = if (isQuoted) "Đã báo giá" else "Đặt trực tiếp"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isResponding, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row — pill + customer
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.fixbid.core.components.StatusPill(
                    text = pillText,
                    color = accent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = booking.customer?.fullName ?: "Khách hàng",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = booking.category.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (booking.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = booking.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))
            FocusMetaRow(icon = Icons.Outlined.LocationOn, text = booking.address)
            Spacer(Modifier.height(4.dp))
            FocusMetaRow(
                icon = Icons.Outlined.Schedule,
                text = "Hẹn ${formatShortDateTime(booking.scheduledAt)} • ${booking.estimatedDurationHours}h"
            )

            // When the worker has already sent a quote, surface the proposed
            // price right on the card so they don't need to open the detail
            // screen to recall what they offered.
            if (isQuoted && booking.quotedPrice != null) {
                Spacer(Modifier.height(4.dp))
                FocusMetaRow(
                    icon = Icons.Outlined.Payments,
                    text = "Đã báo: ${formatCurrencyVnd(booking.quotedPrice)}"
                )
            }

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
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("Từ chối", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onSendQuote,
                    enabled = !isResponding,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    if (isResponding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = if (isQuoted) "Sửa báo giá" else "Báo giá",
                            style = MaterialTheme.typography.labelLarge
                        )
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


/**
 * Quick price-quote dialog opened from a Direct request card on the dashboard.
 * Collects the same three inputs the JobDetailScreen quote sheet does (price,
 * duration, message) but rendered as a compact dialog so the worker doesn't
 * leave the dashboard. Submitting flips the booking to QUOTED and returns
 * control here; the dashboard reload then refreshes the card.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuoteDirectFromDashboardDialog(
    booking: Booking,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (price: Double, durationHours: Double?, message: String) -> Unit
) {
    // Pre-fill with the previous quote when re-quoting (status == QUOTED) so a
    // small adjustment doesn't require retyping.
    var price by remember(booking.id) {
        mutableStateOf(booking.quotedPrice?.toLong()?.toString().orEmpty())
    }
    var durationHours by remember(booking.id) {
        mutableStateOf(
            (booking.quoteEstimatedDurationHours ?: booking.estimatedDurationHours)
                .takeIf { it > 0 }?.toString().orEmpty()
        )
    }
    var message by remember(booking.id) { mutableStateOf(booking.quoteMessage.orEmpty()) }
    var error by remember(booking.id) { mutableStateOf<String?>(null) }

    val isReQuote = booking.status == BookingStatus.QUOTED

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = if (isReQuote) "Cập nhật báo giá" else "Báo giá cho khách",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Khách: ${booking.customer?.fullName ?: "Khách hàng"}\n${booking.category.displayName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = {
                        price = it.filter(Char::isDigit).take(9)
                        error = null
                    },
                    label = { Text("Giá đề xuất (VND)") },
                    placeholder = { Text("vd: 500000") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        price.toDoubleOrNull()?.let {
                            Text(
                                text = formatCurrencyVnd(it),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = durationHours,
                    onValueChange = {
                        durationHours = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            .replace(',', '.')
                        error = null
                    },
                    label = { Text("Thời gian dự kiến (giờ)") },
                    placeholder = { Text("vd: 2") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it; error = null },
                    label = { Text("Lời nhắn cho khách") },
                    placeholder = { Text("Mô tả công việc và cam kết của bạn…") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = "${message.length} ký tự (tối thiểu 10)",
                            fontSize = 11.sp,
                            color = if (message.length >= 10) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                        )
                    }
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val priceValue = price.toDoubleOrNull()
                    val durationValue = durationHours.toDoubleOrNull()
                    when {
                        priceValue == null || priceValue <= 0 ->
                            error = "Vui lòng nhập giá hợp lệ"
                        durationValue != null && durationValue <= 0 ->
                            error = "Thời gian không hợp lệ"
                        message.trim().length < 10 ->
                            error = "Lời nhắn cần ít nhất 10 ký tự"
                        else -> onConfirm(priceValue, durationValue, message.trim())
                    }
                }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isReQuote) "Cập nhật" else "Gửi báo giá", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text("Huỷ") }
        }
    )
}
