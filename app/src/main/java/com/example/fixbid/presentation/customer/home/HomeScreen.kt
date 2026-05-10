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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.fixbid.core.utils.ServiceCategoryMapper
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.core.components.BottomNavbar
import com.example.fixbid.core.components.CategoryGrid
import com.example.fixbid.core.components.NotificationCard
import com.example.fixbid.core.components.PromoBanner
import com.example.fixbid.core.components.SearchBar
import com.example.fixbid.ui.theme.BackgroundGray
import com.example.fixbid.ui.theme.PrimaryBlue
import com.example.fixbid.ui.theme.TextPrimary
import com.example.fixbid.presentation.customer.history.BookingHistoryScreen

import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun HomeScreen(
    onCategoryClick: (ServiceCategory) -> Unit,
    onNotificationClick: () -> Unit,
    onBookingClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavbar(
                selectedIndex = selectedNavIndex,
                onItemSelected = { selectedNavIndex = it }
            )
        },
        containerColor = BackgroundGray,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when (selectedNavIndex) {
            1 -> Box(modifier = Modifier.padding(innerPadding)) {
                BookingHistoryScreen(onBookingClick = onBookingClick)
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
            .background(PrimaryBlue)
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
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Hà Nội",      // TODO: lấy từ GPS thực tế
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
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

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Bạn cần giúp gì nào?",
            color = Color.White,
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
            // Chỉ hiển thị 3 thông báo mới nhất trên HomeScreen
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
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        CategoryGrid(
            categories = categories,
            iconMapper = ServiceCategoryMapper::getIconRes,
            onCategoryClick = onCategoryClick
        )
    }
}