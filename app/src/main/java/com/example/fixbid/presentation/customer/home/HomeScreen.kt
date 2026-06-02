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
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.utils.ServiceCategoryMapper
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.core.components.BottomNavbar
import com.example.fixbid.core.components.CategoryGrid
import com.example.fixbid.core.components.DraggableAiFab
import com.example.fixbid.core.components.NotificationBell
import com.example.fixbid.core.components.NotificationCard
import com.example.fixbid.core.components.PromoBanner
import com.example.fixbid.presentation.customer.history.BookingHistoryScreen
import com.example.fixbid.presentation.customer.profile.ProfileScreen
import com.example.fixbid.presentation.customer.chat.ConversationListScreen
import com.example.fixbid.presentation.customer.chat.ConversationListViewModel
import androidx.compose.runtime.saveable.rememberSaveable

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
    viewModel: HomeViewModel = hiltViewModel(),
    chatListViewModel: ConversationListViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsState()
    val chatUnreadCount by chatListViewModel.unreadCount.collectAsState()
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }

    // Switch to history tab when signaled (History is still index 1)
    LaunchedEffect(showHistoryTab) {
        if (showHistoryTab) {
            selectedNavIndex = 1
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavbar(
                selectedIndex = selectedNavIndex,
                onItemSelected = { selectedNavIndex = it },
                chatUnreadCount = chatUnreadCount
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedNavIndex) {
                1 -> Box(modifier = Modifier.padding(innerPadding)) {
                    BookingHistoryScreen(
                        onBookingClick = onBookingClick,
                        onBiddingWorkersClick = onBiddingWorkersClick,
                        onCompletionConfirmClick = onCompletionConfirmClick,
                        onPaymentClick = onPaymentClick,
                        onReviewClick = onReviewClick,
                        onWorkerProfileClick = onWorkerProfileClick
                    )
                }
                2 -> Box(modifier = Modifier.padding(innerPadding)) {
                    ConversationListScreen(
                        onConversationClick = onChatConversationClick,
                        viewModel = chatListViewModel
                    )
                }
                3 -> Box(modifier = Modifier.padding(innerPadding)) {
                    ProfileScreen(
                        onSignOut = onSignOut,
                        onNotificationSettingsClick = onNotificationSettingsClick
                    )
                }
                else -> Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HomeHeader(
                        onNotificationClick = onNotificationClick,
                        unreadNotificationCount = unreadNotificationCount
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 20.dp, bottom = innerPadding.calculateBottomPadding() + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        PromoBanner()
                        NotificationSection(
                            state = uiState.notificationState,
                            onRetry = viewModel::loadNotifications
                        )
                        FindWorkersCta(onClick = onFindWorkersClick)
                        CategorySection(
                            categories = uiState.categories,
                            onCategoryClick = onCategoryClick
                        )
                    }
                }
            }

            // Draggable AI assistant overlay — only on the home tab so it
            // doesn't cover other screens' primary actions.
            if (selectedNavIndex == 0) {
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

/**
 * Customer home screen header. The previous version included a search bar
 * and a "Bạn cần giúp gì nào?" prompt that did not drive any flow, so it has
 * been simplified to a tight one-row header that only carries the location
 * label and the notification bell.
 */
@Composable
private fun HomeHeader(
    onNotificationClick: () -> Unit,
    unreadNotificationCount: Int = 0,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Vị trí của bạn",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = "Vị trí",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hà Nội",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
            NotificationBell(
                unreadCount = unreadNotificationCount,
                onClick = onNotificationClick,
                tint = MaterialTheme.colorScheme.onPrimary
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
                    .background(Color.LightGray.copy(alpha = 0.3f))
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
                    color = Color.Red.copy(alpha = 0.7f),
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