package com.example.fixbid.presentation.worker.jobdetail

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.components.PhotoEditorScreen
import com.example.fixbid.core.components.StatusPill
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatDateTimeVi
import com.example.fixbid.core.utils.formatRelativeTime
import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.BidStatus
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    onBackClick: () -> Unit,
    onNavigateToCustomer: (String) -> Unit = {},
    viewModel: JobDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Hoisted to the screen level so the photo editor overlay sits above
    // the report-completion ModalBottomSheet (which itself lives in a
    // separate popup window). If we kept this state inside the bottom
    // sheet, dragging the sheet down would dismiss the editor with it.
    var editingUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is JobDetailEvent.Toast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                JobDetailEvent.BidPlaced -> { /* state đã update */ }
                JobDetailEvent.CompletionSubmitted -> { /* state đã update */ }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppHeader(title = "Chi tiết yêu cầu", onBackClick = onBackClick)
        },
        bottomBar = {
            uiState.data?.let { data ->
                JobDetailBottomBar(
                    booking = data.booking,
                    myBid = data.myBid,
                    onPlaceBid = viewModel::openBidDialog,
                    onReportCompletion = viewModel::openCompletionDialog,
                    onNavigateToCustomer = { onNavigateToCustomer(data.booking.id) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = viewModel::load) {
                            Text("Thử lại", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            uiState.data != null -> {
                JobDetailContent(
                    data = uiState.data!!,
                    payment = uiState.payment,
                    contentPadding = innerPadding
                )
            }
        }

        // Bottom sheet đặt thầu
        if (uiState.showBidDialog && uiState.data != null) {
            PlaceBidBottomSheet(
                form = uiState.bidForm,
                competitorBidsCount = uiState.data!!.competitorBidsCount,
                lowestBid = uiState.data!!.lowestBid,
                averageBid = uiState.data!!.averageBid,
                suggestedBudget = uiState.data!!.booking.agreedPrice,
                onDismiss = viewModel::closeBidDialog,
                onPriceChange = viewModel::onPriceChange,
                onDurationChange = viewModel::onDurationChange,
                onMessageChange = viewModel::onMessageChange,
                onSubmit = viewModel::submitBid
            )
        }

        // Bottom sheet báo hoàn thành (ảnh + ghi chú)
        if (uiState.showCompletionDialog) {
            ReportCompletionBottomSheet(
                form = uiState.completionForm,
                onDismiss = viewModel::closeCompletionDialog,
                onNoteChange = viewModel::onCompletionNoteChange,
                onImagesPicked = viewModel::onCompletionImagesSelected,
                onRemoveImage = viewModel::removeCompletionImage,
                onEditImage = { uri -> editingUri = uri },
                onSubmit = { imageBytes -> viewModel.submitCompletion(imageBytes) }
            )
        }
    }

    // Photo editor overlay — wrapped in a Dialog so it renders in its own
    // window above the bottom sheet. The Dialog window sits on top of the
    // ModalBottomSheet's window so the editor is always reachable, and
    // dragging the bottom sheet no longer takes the editor down with it.
    editingUri?.let { current ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { editingUri = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            PhotoEditorScreen(
                sourceUri = current,
                onCancel = { editingUri = null },
                onSave = { savedUri ->
                    viewModel.replaceCompletionImage(current, savedUri)
                    editingUri = null
                }
            )
        }
    }
}

// ─── Content ──────────────────────────────────────────────────────────────────

@Composable
private fun JobDetailContent(
    data: com.example.fixbid.domain.usecase.worker.JobDetailData,
    payment: com.example.fixbid.domain.model.Payment?,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            )
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SummaryCard(booking = data.booking)

        BiddingStatsCard(
            competitorCount = data.competitorBidsCount,
            lowest = data.lowestBid,
            average = data.averageBid,
            highest = data.highestBid
        )

        DescriptionCard(booking = data.booking)

        if (!data.booking.descriptionImages.isNullOrEmpty()) {
            DescriptionImagesCard(images = data.booking.descriptionImages!!)
        }

        InfoCard(booking = data.booking)

        // Receipt card on COMPLETED so the worker sees how much landed in
        // their wallet and when. Hidden until the payment has actually
        // been released from escrow to avoid showing "0đ" prematurely.
        if (data.booking.status == BookingStatus.COMPLETED && payment != null) {
            PayoutCard(payment = payment)
        }

        data.myBid?.let { bid -> MyBidCard(bid = bid) }
    }
}

// ─── Summary (gộp Hero + Budget) ──────────────────────────────────────────────

@Composable
private fun SummaryCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(text = booking.category.displayName, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = formatRelativeTime(booking.createdAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = booking.description,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Payments,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Ngân sách đề xuất",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = booking.agreedPrice?.let { formatCurrencyVnd(it) }
                            ?: "Thoả thuận với khách",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ─── Bidding stats ────────────────────────────────────────────────────────────

@Composable
private fun BiddingStatsCard(
    competitorCount: Int,
    lowest: Double?,
    average: Double?,
    highest: Double?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.QueryStats,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tình hình đấu thầu",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (competitorCount == 0) {
                Text(
                    text = "Chưa có thợ nào đặt giá. Bạn là người đầu tiên!",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCell(
                    modifier = Modifier.weight(1f),
                    label = "Đối thủ",
                    value = "$competitorCount",
                    color = MaterialTheme.colorScheme.primary
                )
                StatCell(
                    modifier = Modifier.weight(1f),
                    label = "Thấp nhất",
                    value = lowest?.let { formatCurrencyVnd(it) } ?: "—",
                    color = AccentGreen
                )
                StatCell(
                    modifier = Modifier.weight(1f),
                    label = "Trung bình",
                    value = average?.let { formatCurrencyVnd(it) } ?: "—",
                    color = StatusGold
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.08f))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── Description ──────────────────────────────────────────────────────────────

@Composable
private fun DescriptionCard(booking: Booking) {
    val note = booking.customerNote?.takeIf { it.isNotBlank() }
    if (note == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.StickyNote2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ghi chú từ khách",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = note,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )
        }
    }
}

// ─── Description images (from customer) ──────────────────────────────────────────────

@Composable
private fun DescriptionImagesCard(images: List<String>) {
    var selectedImage by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Ảnh mô tả công việc",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${images.size} ảnh từ khách hàng",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(images) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Ảnh mô tả",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.medium
                            )
                            .clickable { selectedImage = url }
                    )
                }
            }
        }
    }

    // Full-screen image viewer dialog
    selectedImage?.let { url ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { selectedImage = null }
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black)
                    .clickable { selectedImage = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { selectedImage = null },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Đóng",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─── Info ─────────────────────────────────────────────────────────────────────

@Composable
private fun InfoCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Thông tin",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow(
                icon = Icons.Outlined.LocationOn,
                label = "Địa chỉ",
                value = booking.address
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            DetailRow(
                icon = Icons.Outlined.Schedule,
                label = "Thời gian hẹn",
                value = formatDateTimeVi(booking.scheduledAt)
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            DetailRow(
                icon = Icons.Outlined.Timer,
                label = "Thời gian dự kiến",
                value = "${booking.estimatedDurationHours} giờ"
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── Payout receipt (worker, COMPLETED) ───────────────────────────────────────

@Composable
private fun PayoutCard(payment: com.example.fixbid.domain.model.Payment) {
    val released = payment.escrowStatus == com.example.fixbid.domain.model.EscrowStatus.RELEASED ||
        payment.status == com.example.fixbid.domain.model.PaymentStatus.COMPLETED

    val (accentColor, statusLabel) = if (released) {
        AccentGreen to "Đã chuyển vào ví"
    } else {
        StatusOrange to "Đang chờ chuyển"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Payments,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thanh toán",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        color = accentColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            PayoutLine(label = "Khách thanh toán", value = formatCurrencyVnd(payment.amount))
            Spacer(modifier = Modifier.height(6.dp))
            PayoutLine(
                label = "Phí nền tảng (${com.example.fixbid.core.utils.PaymentConstants.PLATFORM_FEE_LABEL})",
                value = "- ${formatCurrencyVnd(payment.platformFee)}"
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bạn nhận được",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatCurrencyVnd(payment.workerReceives),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
            }

            payment.releasedAt?.let { ts ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Chuyển lúc ${formatDateTimeVi(ts)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PayoutLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── My bid ───────────────────────────────────────────────────────────────────

@Composable
private fun MyBidCard(bid: Bid) {
    val (statusColor, statusLabel) = when (bid.status) {
        BidStatus.PENDING -> StatusOrange to "Đang chờ khách xét"
        BidStatus.ACCEPTED -> AccentGreen to "Đã được chọn"
        BidStatus.REJECTED -> StatusRed to "Bị từ chối"
        BidStatus.WITHDRAWN -> MaterialTheme.colorScheme.onSurfaceVariant to "Đã rút"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AssignmentTurnedIn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Báo giá của bạn",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                StatusPill(text = statusLabel, color = statusColor)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Giá đề xuất", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatCurrencyVnd(bid.proposedPrice),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Thời gian", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${bid.estimatedDurationHours}h",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (bid.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "“${bid.message}”",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

// ─── Bottom bar ───────────────────────────────────────────────────────────────

@Composable
private fun JobDetailBottomBar(
    booking: Booking,
    myBid: Bid?,
    onPlaceBid: () -> Unit,
    onReportCompletion: () -> Unit,
    onNavigateToCustomer: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            when (booking.status) {
                // Bidding stage — worker đặt giá
                BookingStatus.BIDDING, BookingStatus.PENDING -> {
                    if (myBid == null || myBid.status == BidStatus.WITHDRAWN
                        || myBid.status == BidStatus.REJECTED) {
                        PrimaryActionButton(
                            label = if (myBid == null) "Đặt giá thầu" else "Đặt lại giá",
                            icon = Icons.Outlined.Gavel,
                            onClick = onPlaceBid
                        )
                    } else {
                        StatusInfoRow(
                            isPositive = myBid.status == BidStatus.ACCEPTED,
                            text = if (myBid.status == BidStatus.ACCEPTED)
                                "Khách đã chọn bạn cho công việc này"
                            else "Bạn đã đặt giá. Chờ khách phản hồi."
                        )
                    }
                }

                // Khách đã chọn thợ, đang chờ thanh toán
                BookingStatus.AWAITING_PAYMENT -> {
                    StatusInfoRow(
                        isPositive = false,
                        text = "Khách đang tiến hành thanh toán. Vui lòng chờ."
                    )
                }

                // Thợ đã được chọn → vào làm việc
                BookingStatus.CONFIRMED -> {
                    Column {
                        StatusInfoRow(
                            isPositive = true,
                            text = "Bạn đã được chọn. Hãy đến đúng giờ và bắt đầu làm việc."
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PrimaryActionButton(
                            label = "Chỉ đường tới khách",
                            icon = Icons.Outlined.Directions,
                            onClick = onNavigateToCustomer
                        )
                    }
                }

                // Đang làm → báo hoàn thành + cho phép xem chỉ đường
                BookingStatus.IN_PROGRESS -> {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onNavigateToCustomer,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    Icons.Outlined.Directions,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chỉ đường", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = onReportCompletion,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    Icons.Outlined.AssignmentTurnedIn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hoàn thành", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                BookingStatus.PENDING_COMPLETION -> {
                    StatusInfoRow(
                        isPositive = true,
                        text = "Đã gửi báo cáo. Chờ khách xác nhận hoàn thành."
                    )
                }

                BookingStatus.COMPLETED -> {
                    StatusInfoRow(
                        isPositive = true,
                        text = "Công việc đã hoàn thành. Cảm ơn bạn!"
                    )
                }

                BookingStatus.CANCELLED, BookingStatus.DISPUTED -> {
                    StatusInfoRow(
                        isPositive = false,
                        text = "Công việc không còn hoạt động."
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun StatusInfoRow(isPositive: Boolean, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isPositive) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (isPositive) AccentGreen else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─── Place bid bottom sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceBidBottomSheet(
    form: BidFormState,
    competitorBidsCount: Int,
    lowestBid: Double?,
    averageBid: Double?,
    suggestedBudget: Double?,
    onDismiss: () -> Unit,
    onPriceChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!form.isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Gavel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Đặt giá thầu",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Thuyết phục khách hàng chọn bạn",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Market hint
            if (competitorBidsCount > 0 || suggestedBudget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.QueryStats,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        if (competitorBidsCount > 0) {
                            Text(
                                text = "$competitorBidsCount thợ khác đã đặt giá",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    lowestBid?.let { append("Thấp ${formatCurrencyVnd(it)}") }
                                    if (lowestBid != null && averageBid != null) append(" • ")
                                    averageBid?.let { append("TB ${formatCurrencyVnd(it)}") }
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (suggestedBudget != null) {
                            Text(
                                text = "Khách đề xuất ${formatCurrencyVnd(suggestedBudget)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Hãy đặt giá hợp lý để tăng cơ hội",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Price field
            OutlinedTextField(
                value = form.price,
                onValueChange = onPriceChange,
                label = { Text("Giá đề xuất (VND)") },
                placeholder = { Text("vd: 500000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                enabled = !form.isSubmitting,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                supportingText = {
                    form.price.toDoubleOrNull()?.let {
                        Text(
                            text = formatCurrencyVnd(it),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )

            // Duration field with quick chips
            Column {
                OutlinedTextField(
                    value = form.durationHours,
                    onValueChange = onDurationChange,
                    label = { Text("Thời gian dự kiến (giờ)") },
                    placeholder = { Text("vd: 2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    enabled = !form.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1", "2", "4", "8").forEach { hours ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (form.durationHours == hours) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = !form.isSubmitting) {
                                    onDurationChange(hours)
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${hours}h",
                                fontSize = 12.sp,
                                color = if (form.durationHours == hours) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Message
            OutlinedTextField(
                value = form.message,
                onValueChange = onMessageChange,
                label = { Text("Lời giới thiệu") },
                placeholder = {
                    Text("Giới thiệu kinh nghiệm và cam kết với khách hàng…")
                },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                enabled = !form.isSubmitting,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                supportingText = {
                    Text(
                        text = "${form.message.length} ký tự (tối thiểu 10)",
                        fontSize = 11.sp,
                        color = if (form.message.length >= 10) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
            )

            // Error
            if (form.errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = form.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }

            // Submit
            Button(
                onClick = onSubmit,
                enabled = !form.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.medium
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gửi báo giá", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ─── Report completion bottom sheet ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportCompletionBottomSheet(
    form: CompletionFormState,
    onDismiss: () -> Unit,
    onNoteChange: (String) -> Unit,
    onImagesPicked: (List<Uri>) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onEditImage: (Uri) -> Unit,
    onSubmit: (List<Pair<String, ByteArray>>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onImagesPicked(uris)
    }

    ModalBottomSheet(
        onDismissRequest = { if (!form.isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AssignmentTurnedIn,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Báo hoàn thành",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Gửi ảnh và ghi chú để khách xác nhận",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chụp ảnh kết quả thực tế giúp khách an tâm và xác nhận nhanh hơn.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
            }

            // Image picker
            Column {
                Text(
                    text = "Ảnh thực tế (1-5 ảnh)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Add tile
                    if (form.selectedImageUris.size < 5) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .clickable(enabled = !form.isSubmitting) {
                                        pickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.AddAPhoto,
                                        contentDescription = "Thêm ảnh",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Thêm ảnh",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    items(form.selectedImageUris, key = { it.toString() }) { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .clickable(enabled = !form.isSubmitting) { onEditImage(uri) }
                            )
                            // Remove (top-end)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable(enabled = !form.isSubmitting) { onRemoveImage(uri) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Xoá",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            // Edit pill (bottom-center) — opens the photo
                            // editor so the worker can highlight what
                            // they fixed before sending it to the customer.
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable(enabled = !form.isSubmitting) { onEditImage(uri) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Chỉnh sửa",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Sửa",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${form.selectedImageUris.size}/5 ảnh",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Note field
            OutlinedTextField(
                value = form.note,
                onValueChange = onNoteChange,
                label = { Text("Ghi chú cho khách (tuỳ chọn)") },
                placeholder = {
                    Text("Mô tả công việc đã làm, những lưu ý sau sửa chữa...")
                },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                enabled = !form.isSubmitting,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Error
            if (form.errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = form.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }

            // Submit
            Button(
                onClick = {
                    val resolved = resolveImageBytes(context, form.selectedImageUris)
                    onSubmit(resolved)
                },
                enabled = !form.isSubmitting && form.selectedImageUris.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = MaterialTheme.shapes.medium
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Gửi báo cáo hoàn thành",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

private fun resolveImageBytes(
    context: android.content.Context,
    uris: List<Uri>
): List<Pair<String, ByteArray>> {
    return uris.mapIndexedNotNull { index, uri ->
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                val name = "completion_${System.currentTimeMillis()}_$index.jpg"
                name to bytes
            }
        }.getOrNull()
    }
}