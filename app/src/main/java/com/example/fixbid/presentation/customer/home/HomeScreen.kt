package com.example.fixbid.presentation.customer.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
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
import com.example.fixbid.core.components.NotificationCard
import com.example.fixbid.core.components.PromoBanner
import com.example.fixbid.core.components.SearchBar
import com.example.fixbid.presentation.customer.history.BookingHistoryScreen
import com.example.fixbid.presentation.customer.profile.ProfileScreen
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun HomeScreen(
    onCategoryClick: (ServiceCategory) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onBookingClick: (String) -> Unit = {},
    onCompletionConfirmClick: (String) -> Unit = {},
    onPaymentClick: (String) -> Unit = {},
    onSignOut: () -> Unit = {},
    showHistoryTab: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsState()
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }

    // Switch to history tab when signaled
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
                onItemSelected = { selectedNavIndex = it }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when (selectedNavIndex) {
            1 -> Box(modifier = Modifier.padding(innerPadding)) {
                BookingHistoryScreen(
                    onBookingClick = onBookingClick,
                    onCompletionConfirmClick = onCompletionConfirmClick,
                    onPaymentClick = onPaymentClick
                )
            }
            2 -> Box(modifier = Modifier.padding(innerPadding)) {
                ProfileScreen(onSignOut = onSignOut)
            }
            else -> Column(
                modifier = Modifier.fillMaxSize()
            ) {
                HomeHeader(
                    searchQuery = uiState.searchQuery,
                    onSearchChange = viewModel::onSearchQueryChange,
                    onNotificationClick = onNotificationClick
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
                    CategorySection(
                        categories = uiState.categories,
                        onCategoryClick = onCategoryClick
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onNotificationClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = "Vị trí",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Hà Nội",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
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

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Bạn cần giúp gì nào?",
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange
        )

        Spacer(modifier = Modifier.height(8.dp))
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