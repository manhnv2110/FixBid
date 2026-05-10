package com.example.fixbid.presentation.customer.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.ui.theme.BackgroundGray
import com.example.fixbid.ui.theme.LightBlue
import com.example.fixbid.ui.theme.PrimaryBlue
import com.example.fixbid.ui.theme.TextPrimary
import com.example.fixbid.ui.theme.TextSecondary

// ─── Stub data models ───────────────────────────────────────────────────────

enum class BookingStatus(val label: String, val color: Color) {
    BIDDING("Đang nhận báo giá", Color(0xFF00897B)),
    CONFIRMED("Đã chọn thợ", Color(0xFF1565C0)),
    CANCELLED("Đã huỷ", Color(0xFFB0BEC5)),
    DONE("Hoàn thành", Color(0xFF43A047))
}

data class BookingItem(
    val id: String,
    val category: String,
    val description: String,
    val address: String,
    val date: String,
    val status: BookingStatus,
    val bidCount: Int = 0,
    val workerName: String? = null,
    val finalPrice: String? = null
)

// Sample stub data – replace with real ViewModel / repository calls later
private val sampleBooked = listOf(
    BookingItem(
        id = "1",
        category = "Sửa điện",
        description = "Đèn phòng khách chập chờn, cần kiểm tra lại toàn bộ hệ thống.",
        address = "12 Nguyễn Trãi, Hà Nội",
        date = "10/05/2026",
        status = BookingStatus.BIDDING,
        bidCount = 3
    ),
    BookingItem(
        id = "2",
        category = "Điều hòa",
        description = "Điều hòa không lạnh, cần vệ sinh và nạp gas.",
        address = "45 Lê Duẩn, Đà Nẵng",
        date = "09/05/2026",
        status = BookingStatus.CONFIRMED,
        bidCount = 5,
        workerName = "Anh Minh",
        finalPrice = "200.000 đ"
    ),
    BookingItem(
        id = "3",
        category = "Khóa cửa",
        description = "Mất chìa khóa, cần thay ổ khóa mới.",
        address = "88 Trần Hưng Đạo, HCM",
        date = "07/05/2026",
        status = BookingStatus.CANCELLED
    )
)

private val sampleDone = listOf(
    BookingItem(
        id = "4",
        category = "Sửa ống nước",
        description = "Ống nước nhà bếp bị rò, đã được thay mới hoàn toàn.",
        address = "20 Bạch Đằng, Hà Nội",
        date = "01/05/2026",
        status = BookingStatus.DONE,
        workerName = "Anh Tuấn",
        finalPrice = "350.000 đ"
    ),
    BookingItem(
        id = "5",
        category = "Vệ sinh",
        description = "Tổng vệ sinh căn hộ 2 phòng ngủ.",
        address = "33 Điện Biên Phủ, HCM",
        date = "25/04/2026",
        status = BookingStatus.DONE,
        workerName = "Chị Hoa",
        finalPrice = "500.000 đ"
    )
)

// ─── Screen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingHistoryScreen(
    onBookingClick: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Đã đặt", "Đã làm")

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
                text = "Lịch sử đặt lịch",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = PrimaryBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PrimaryBlue
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) PrimaryBlue else TextSecondary
                        )
                    }
                )
            }
        }

        // Content
        val items = if (selectedTab == 0) sampleBooked else sampleDone

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chưa có dịch vụ nào", color = TextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = items,
                    key = { it.id }
                ) { item ->
                    BookingCard(
                        item = item, 
                        isDone = selectedTab == 1,
                        onClick = {
                            if (item.status == BookingStatus.BIDDING) {
                                onBookingClick(item.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── Card ────────────────────────────────────────────────────────────────────

@Composable
private fun BookingCard(
    item: BookingItem, 
    isDone: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDone && item.status == BookingStatus.BIDDING, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: category + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.category,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                StatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.description,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Address & date row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📍 ${item.address}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.date,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Bid info (only in "Đã đặt" tab)
            if (!isDone && item.bidCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(LightBlue)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${item.bidCount} báo giá",
                            fontSize = 12.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (item.workerName != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đã chọn: ${item.workerName}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Completed or Confirmed info (for "Đã làm" tab or CONFIRMED status)
            if (isDone || item.status == BookingStatus.CONFIRMED) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.workerName != null) {
                        Text(
                            text = "🔧 Thợ: ${item.workerName}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    if (item.finalPrice != null) {
                        Text(
                            text = item.finalPrice,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }
            }
        }
    }
}

// ─── Status badge ────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(status: BookingStatus) {
    val bgColor = status.color.copy(alpha = 0.12f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = status.color
        )
    }
}
