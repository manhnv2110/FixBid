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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatDateTimeVi
import com.example.fixbid.core.utils.formatRelativeTime
import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.BidStatus
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.ui.theme.*

@Composable
fun JobDetailScreen(
    onBackClick: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is JobDetailEvent.Toast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                JobDetailEvent.BidPlaced -> { /* handled via state */ }
                JobDetailEvent.CompletionSubmitted -> onBackClick()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            JobDetailTopBar(onBackClick = onBackClick)
        },
        bottomBar = {
            uiState.data?.let { data ->
                val booking = data.booking
                when (booking.status) {
                    BookingStatus.IN_PROGRESS -> {
                        CompletionBottomBar(onCompleteClick = viewModel::openCompletionDialog)
                    }
                    BookingStatus.PENDING_COMPLETION -> {
                        PendingCompletionBottomBar()
                    }
                    else -> {
                        JobDetailBottomBar(
                            myBid = data.myBid,
                            onPlaceBid = viewModel::openBidDialog
                        )
                    }
                }
            }
        },
        containerColor = BackgroundGray,
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
                    CircularProgressIndicator(color = PrimaryBlue)
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
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = viewModel::load) {
                            Text("Thử lại", color = PrimaryBlue)
                        }
                    }
                }
            }
            uiState.data != null -> {
                JobDetailContent(
                    data = uiState.data!!,
                    contentPadding = innerPadding
                )
            }
        }

        if (uiState.showBidDialog) {
            PlaceBidDialog(
                form = uiState.bidForm,
                onDismiss = viewModel::closeBidDialog,
                onPriceChange = viewModel::onPriceChange,
                onDurationChange = viewModel::onDurationChange,
                onMessageChange = viewModel::onMessageChange,
                onSubmit = viewModel::submitBid
            )
        }

        if (uiState.showCompletionDialog) {
            CompletionDialog(
                form = uiState.completionForm,
                onDismiss = viewModel::closeCompletionDialog,
                onNoteChange = viewModel::onCompletionNoteChange,
                onImagesSelected = viewModel::onCompletionImagesSelected,
                onRemoveImage = viewModel::removeCompletionImage,
                onSubmit = { imageBytesList ->
                    viewModel.submitCompletion(imageBytesList)
                }
            )
        }
    }
}

@Composable
private fun JobDetailTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrimaryBlue)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Quay lại",
                tint = Color.White
            )
        }
        Text(
            text = "Chi tiết yêu cầu",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun JobDetailContent(
    data: com.example.fixbid.domain.usecase.worker.JobDetailData,
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
        // Hero card with category + budget
        HeroCard(booking = data.booking)

        // Description
        SectionCard(title = "Mô tả công việc") {
            Text(
                text = data.booking.description,
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 22.sp
            )
            data.booking.customerNote?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LightBlue.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Row {
                        Icon(
                            Icons.Outlined.StickyNote2,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = note,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Location & schedule
        SectionCard(title = "Thông tin") {
            DetailRow(
                icon = Icons.Outlined.LocationOn,
                label = "Địa chỉ",
                value = data.booking.address
            )
            HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(vertical = 10.dp))
            DetailRow(
                icon = Icons.Outlined.Schedule,
                label = "Thời gian hẹn",
                value = formatDateTimeVi(data.booking.scheduledAt)
            )
            HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(vertical = 10.dp))
            DetailRow(
                icon = Icons.Outlined.Timer,
                label = "Thời gian dự kiến",
                value = "${data.booking.estimatedDurationHours} giờ"
            )
            HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(vertical = 10.dp))
            DetailRow(
                icon = Icons.Outlined.AccessTime,
                label = "Đã đăng",
                value = formatRelativeTime(data.booking.createdAt)
            )
        }

        // Bidding stats
        SectionCard(title = "Tình hình đấu thầu") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BidStatCell(
                    modifier = Modifier.weight(1f),
                    label = "Đối thủ",
                    value = "${data.competitorBidsCount}",
                    iconTint = PrimaryBlue
                )
                BidStatCell(
                    modifier = Modifier.weight(1f),
                    label = "Giá thấp",
                    value = data.lowestBid?.let { formatCurrencyVnd(it) } ?: "—",
                    iconTint = AccentGreen
                )
                BidStatCell(
                    modifier = Modifier.weight(1f),
                    label = "Trung bình",
                    value = data.averageBid?.let { formatCurrencyVnd(it) } ?: "—",
                    iconTint = Color(0xFFFFA726)
                )
            }
        }

        // My bid (if exists)
        data.myBid?.let { bid ->
            MyBidCard(bid = bid)
        }
    }
}

@Composable
private fun HeroCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(LightBlue)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = booking.category.displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Yêu cầu ${booking.category.displayName.lowercase()}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Payments,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Ngân sách đề xuất",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = booking.agreedPrice?.let { formatCurrencyVnd(it) }
                            ?: "Thoả thuận với khách",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
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
            tint = PrimaryBlue,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(
                text = value,
                fontSize = 14.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BidStatCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    iconTint: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundGray)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = iconTint
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun MyBidCard(bid: Bid) {
    val statusColor = when (bid.status) {
        BidStatus.PENDING -> Color(0xFFF57C00)
        BidStatus.ACCEPTED -> AccentGreen
        BidStatus.REJECTED -> Color(0xFFD32F2F)
        BidStatus.WITHDRAWN -> TextSecondary
    }
    val statusLabel = when (bid.status) {
        BidStatus.PENDING -> "Đang chờ khách xét"
        BidStatus.ACCEPTED -> "Đã được chọn"
        BidStatus.REJECTED -> "Bị từ chối"
        BidStatus.WITHDRAWN -> "Đã rút"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightBlue.copy(alpha = 0.4f)),
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
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Báo giá của bạn",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Giá đề xuất", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = formatCurrencyVnd(bid.proposedPrice),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Thời gian", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = "${bid.estimatedDurationHours}h",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            if (bid.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "\"${bid.message}\"",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun JobDetailBottomBar(
    myBid: Bid?,
    onPlaceBid: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            if (myBid == null || myBid.status == BidStatus.WITHDRAWN
                || myBid.status == BidStatus.REJECTED) {
                Button(
                    onClick = onPlaceBid,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        Icons.Outlined.Gavel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (myBid == null) "Đặt giá thầu" else "Đặt lại giá",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (myBid.status) {
                            BidStatus.ACCEPTED -> "Khách đã chọn bạn cho công việc này"
                            else -> "Bạn đã đặt giá. Chờ khách phản hồi"
                        },
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceBidDialog(
    form: BidFormState,
    onDismiss: () -> Unit,
    onPriceChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!form.isSubmitting) onDismiss() },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LightBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Gavel,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Đặt giá thầu",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.price,
                    onValueChange = onPriceChange,
                    label = { Text("Giá đề xuất (VND)") },
                    placeholder = { Text("vd: 500000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !form.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    supportingText = {
                        form.price.toDoubleOrNull()?.let {
                            Text(
                                text = formatCurrencyVnd(it),
                                color = PrimaryBlue,
                                fontSize = 12.sp
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = form.durationHours,
                    onValueChange = onDurationChange,
                    label = { Text("Thời gian dự kiến (giờ)") },
                    placeholder = { Text("vd: 2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !form.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

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
                    shape = RoundedCornerShape(10.dp),
                    enabled = !form.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                if (form.errorMessage != null) {
                    Text(
                        form.errorMessage,
                        color = Color(0xFFD32F2F),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !form.isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Gửi báo giá", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !form.isSubmitting
            ) {
                Text("Hủy", color = TextSecondary)
            }
        }
    )
}


@Composable
private fun CompletionBottomBar(onCompleteClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Button(
                onClick = onCompleteClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Báo cáo hoàn thành",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun PendingCompletionBottomBar() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.HourglassTop,
                contentDescription = null,
                tint = Color(0xFFE65100),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Đang chờ khách hàng xác nhận hoàn thành",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE65100)
            )
        }
    }
}

@Composable
private fun CompletionDialog(
    form: CompletionFormState,
    onDismiss: () -> Unit,
    onNoteChange: (String) -> Unit,
    onImagesSelected: (List<Uri>) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSubmit: (List<Pair<String, ByteArray>>) -> Unit
) {
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onImagesSelected(uris)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!form.isSubmitting) onDismiss() },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Báo cáo hoàn thành",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Chụp ảnh thực tế sau khi hoàn thành để gửi cho khách xác nhận:",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 19.sp
                )

                // Image picker button
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !form.isSubmitting
                ) {
                    Icon(
                        Icons.Outlined.AddAPhoto,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Chọn ảnh (${form.selectedImageUris.size}/5)",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Selected images preview
                if (form.selectedImageUris.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(form.selectedImageUris) { uri ->
                            Box(modifier = Modifier.size(80.dp)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Ảnh đã chọn",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            1.dp,
                                            Color(0xFFE0E0E0),
                                            RoundedCornerShape(10.dp)
                                        )
                                )
                                // Remove button
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD32F2F))
                                        .clickable(enabled = !form.isSubmitting) {
                                            onRemoveImage(uri)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Xóa ảnh",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Note input
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNoteChange,
                    label = { Text("Ghi chú cho khách (tuỳ chọn)") },
                    placeholder = {
                        Text("Mô tả công việc đã thực hiện, lưu ý cho khách...")
                    },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !form.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                if (form.errorMessage != null) {
                    Text(
                        form.errorMessage,
                        color = Color(0xFFD32F2F),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Read image bytes from URIs
                    val imageBytesList = form.selectedImageUris.mapIndexed { index, uri ->
                        val bytes = context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes() } ?: ByteArray(0)
                        val extension = context.contentResolver.getType(uri)
                            ?.substringAfter("/") ?: "jpg"
                        val fileName = "img_${System.currentTimeMillis()}_$index.$extension"
                        fileName to bytes
                    }.filter { it.second.isNotEmpty() }

                    onSubmit(imageBytesList)
                },
                enabled = !form.isSubmitting && form.selectedImageUris.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Gửi cho khách", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !form.isSubmitting
            ) {
                Text("Hủy", color = TextSecondary)
            }
        }
    )
}