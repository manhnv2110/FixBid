package com.example.fixbid.presentation.customer.bidding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.utils.toRelativeTime
import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.BidStatus
import com.example.fixbid.ui.theme.BackgroundGray
import com.example.fixbid.ui.theme.PrimaryBlue
import com.example.fixbid.ui.theme.TextPrimary
import com.example.fixbid.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiddingWorkersScreen(
    bookingId: String,
    onBackClick: () -> Unit,
    onWorkerClick: (String) -> Unit,
    viewModel: BiddingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Danh sách báo giá",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundGray
                )
            )
        },
        containerColor = BackgroundGray
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is BiddingUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                }
                is BiddingUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, color = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = viewModel::loadBids) {
                                Text("Thử lại", color = PrimaryBlue)
                            }
                        }
                    }
                }
                is BiddingUiState.Success -> {
                    if (state.bids.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Chưa có báo giá nào",
                                    color = TextSecondary,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Các thợ sẽ sớm gửi báo giá cho bạn",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Kết quả (${state.bids.size})",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = state.bids,
                                key = { it.id }
                            ) { bid ->
                                WorkerBidCard(
                                    bid = bid,
                                    onClick = { onWorkerClick(bid.workerId) },
                                    onAccept = { viewModel.acceptBid(bid.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerBidCard(
    bid: Bid,
    onClick: () -> Unit,
    onAccept: () -> Unit
) {
    val priceFormatted = NumberFormat.getNumberInstance(Locale("vi", "VN"))
        .format(bid.proposedPrice.toLong()) + " đ"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Avatar, Name, Price
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar placeholder
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (bid.worker?.fullName?.firstOrNull() ?: "T").toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bid.worker?.fullName ?: "Thợ #${bid.workerId.take(6)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = bid.createdAt.toRelativeTime(),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Text(
                    text = priceFormatted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Message from worker
            if (bid.message.isNotBlank()) {
                Text(
                    text = bid.message,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Duration estimate
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Duration",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Ước tính: ${bid.estimatedDurationHours}h",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status / Accept button
            when (bid.status) {
                BidStatus.PENDING -> {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Chọn thợ này", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                BidStatus.ACCEPTED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Accepted",
                            tint = Color(0xFF43A047),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Đã chọn",
                            color = Color(0xFF43A047),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                BidStatus.REJECTED -> {
                    Text(
                        text = "Đã từ chối",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                BidStatus.WITHDRAWN -> {
                    Text(
                        text = "Thợ đã rút báo giá",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
