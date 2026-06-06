package com.example.fixbid.presentation.customer.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.fixbid.core.utils.ServiceCategoryMapper
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.core.components.AppBottomBar
import com.example.fixbid.core.components.BottomNavDestination
import com.example.fixbid.core.components.CategoryGrid
import com.example.fixbid.core.components.DraggableAiFab
import com.example.fixbid.core.components.NotificationBell
import com.example.fixbid.core.components.NotificationCard
import com.example.fixbid.core.components.PrimaryTopBar
import com.example.fixbid.core.components.PromoBanner
import com.example.fixbid.presentation.customer.history.BookingHistoryScreen
import com.example.fixbid.presentation.customer.profile.ProfileScreen
import com.example.fixbid.presentation.customer.chat.ConversationListScreen
import com.example.fixbid.presentation.customer.chat.ConversationListViewModel

private object CustomerTab {
    const val HOME = "tab_home"
    const val HISTORY = "tab_history"
    const val CHAT = "tab_chat"
    const val PROFILE = "tab_profile"
    val ordered = listOf(HOME, HISTORY, CHAT, PROFILE)
}

/**
 * Customer shell. Hosts the four primary tabs in a nested [NavHost] tied to its
 * own [androidx.navigation.NavController] so each tab keeps an independent back
 * stack and scroll/form state, and the bottom bar selection follows the real
 * navigation state. Detail destinations (booking detail, payment, chat thread…)
 * still live on the outer NavController via the hoisted callbacks.
 */
@Composable
fun HomeScreen(
    onCategoryClick: (ServiceCategory) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onBookingClick: (String) -> Unit = {},
    onBiddingWorkersClick: (String) -> Unit = {},
    onCompletionConfirmClick: (String) -> Unit = {},
    onPaymentClick: (String) -> Unit = {},
    onReviewClick: (String) -> Unit = {},
    onWorkerProfileClick: (String) -> Unit = {},
    onSignOut: () -> Unit = {},
    showHistoryTab: Boolean = false,
    onChatConversationClick: (conversationId: String, workerId: String, workerName: String) -> Unit = { _, _, _ -> },
    onNotificationSettingsClick: () -> Unit = {},
    onFindWorkersClick: () -> Unit = {},
    onChatbotClick: () -> Unit = {},
    onWalletClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    chatListViewModel: ConversationListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val chatUnreadCount by chatListViewModel.unreadCount.collectAsState()

    val tabNavController = rememberNavController()
    val currentRoute = tabNavController
        .currentBackStackEntryAsState().value?.destination?.route
        ?: CustomerTab.HOME
    val selectedIndex = CustomerTab.ordered.indexOf(currentRoute).coerceAtLeast(0)

    val destinations = listOf(
        BottomNavDestination("Trang chủ", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavDestination("Lịch sử", Icons.Filled.History, Icons.Outlined.History),
        BottomNavDestination("Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, badgeCount = chatUnreadCount),
        BottomNavDestination("Hồ sơ", Icons.Filled.Person, Icons.Outlined.Person)
    )

    // Deep-link / signal: jump to the History tab when asked.
    LaunchedEffect(showHistoryTab) {
        if (showHistoryTab) {
            tabNavController.navigate(CustomerTab.HISTORY) {
                popUpTo(CustomerTab.HOME) { saveState = true }
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
                onItemSelected = { index ->
                    val route = CustomerTab.ordered[index]
                    if (route != currentRoute) {
                        tabNavController.navigate(route) {
                            popUpTo(CustomerTab.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = tabNavController,
                startDestination = CustomerTab.HOME,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(CustomerTab.HOME) {
                    HomeTabContent(
                        uiState = uiState,
                        bottomPadding = innerPadding.calculateBottomPadding(),
                        onNotificationClick = onNotificationClick,
                        unreadNotificationCount = unreadNotificationCount,
                        onFindWorkersClick = onFindWorkersClick,
                        onCategoryClick = onCategoryClick,
                        onRetryNotifications = viewModel::loadNotifications
                    )
                }
                composable(CustomerTab.HISTORY) {
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        BookingHistoryScreen(
                            onBookingClick = onBookingClick,
                            onBiddingWorkersClick = onBiddingWorkersClick,
                            onCompletionConfirmClick = onCompletionConfirmClick,
                            onPaymentClick = onPaymentClick,
                            onReviewClick = onReviewClick,
                            onWorkerProfileClick = onWorkerProfileClick
                        )
                    }
                }
                composable(CustomerTab.CHAT) {
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        ConversationListScreen(
                            onConversationClick = onChatConversationClick,
                            viewModel = chatListViewModel
                        )
                    }
                }
                composable(CustomerTab.PROFILE) {
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        ProfileScreen(
                            onSignOut = onSignOut,
                            onNotificationSettingsClick = onNotificationSettingsClick,
                            onWalletClick = onWalletClick
                        )
                    }
                }
            }

            // Draggable AI assistant overlay — only on the home tab so it
            // doesn't cover other screens' primary actions.
            if (currentRoute == CustomerTab.HOME) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    DraggableAiFab(
                        onClick = onChatbotClick,
                        storageKey = "customer_ai_fab"
                    )
                }
            }
        }
    }
}

/** Home tab body: brand header, promo, recent notifications, CTAs and categories. */
@Composable
private fun HomeTabContent(
    uiState: HomeUiState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onNotificationClick: () -> Unit,
    unreadNotificationCount: Int,
    onFindWorkersClick: () -> Unit,
    onCategoryClick: (ServiceCategory) -> Unit,
    onRetryNotifications: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTopBar(
            title = "Hà Nội",
            subtitle = "Vị trí của bạn",
            actions = {
                NotificationBell(
                    unreadCount = unreadNotificationCount,
                    onClick = onNotificationClick,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = bottomPadding + 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            PromoBanner()
            NotificationSection(
                state = uiState.notificationState,
                onRetry = onRetryNotifications
            )
            FindWorkersCta(onClick = onFindWorkersClick)
            CategorySection(
                categories = uiState.categories,
                onCategoryClick = onCategoryClick
            )
        }
    }
}

@Composable
private fun NotificationSection(
    state: NotificationUiState,
    onRetry: () -> Unit
) {
    when (state) {
        is NotificationUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            )
        }
        is NotificationUiState.Success -> {
            val recent = state.notifications
                .filter { !it.isRead }
                .take(3)

            if (recent.isNotEmpty()) {
                NotificationCard(
                    notifications = recent,
                )
            }
        }
        is NotificationUiState.Error -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
                TextButton(onClick = onRetry) {
                    Text("Thử lại")
                }
            }
        }
    }
}

@Composable
private fun FindWorkersCta(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
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
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tìm thợ trực tiếp",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Xem hồ sơ, đánh giá và đặt thẳng thợ bạn tin tưởng",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CategorySection(
    categories: List<ServiceCategory>,
    onCategoryClick: (ServiceCategory) -> Unit
) {
    Column {
        Text(
            text = "Danh mục dịch vụ",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        CategoryGrid(
            categories = categories,
            iconMapper = ServiceCategoryMapper::getIconRes,
            onCategoryClick = onCategoryClick
        )
    }
}