package com.example.fixbid.presentation.customer.payment

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentStatus

@Composable
fun PaymentScreen(
    onBackClick: () -> Unit,
    onPaymentSuccess: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PaymentEvent.Toast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is PaymentEvent.OpenVNPayUrl -> {
                    // Mở VNPay payment URL trong browser
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                    context.startActivity(intent)
                }
                PaymentEvent.PaymentCompleted -> onPaymentSuccess()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { PaymentTopBar(onBackClick = onBackClick) },
        bottomBar = {
            if (!uiState.paymentSuccess && uiState.booking != null) {
                PaymentBottomBar(
                    isProcessing = uiState.isProcessing,
                    onPayClick = viewModel::initiateVNPayPayment
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Đang tải thông tin...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            uiState.errorMessage != null && uiState.booking == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = viewModel::loadBookingDetails,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử lại")
                        }
                    }
                }
            }
            uiState.paymentSuccess -> {
                PaymentSuccessContent(
                    booking = uiState.booking!!,
                    payment = uiState.payment,
                    contentPadding = innerPadding,
                    onContinue = onPaymentSuccess
                )
            }
            uiState.booking != null -> {
                PaymentContent(
                    booking = uiState.booking!!,
                    payment = uiState.payment,
                    errorMessage = uiState.errorMessage,
                    contentPadding = innerPadding
                )
            }
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun PaymentTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Quay lại",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            text = "Thanh toán",
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

// ─── Payment Content ──────────────────────────────────────────────────────────

@Composable
private fun PaymentContent(
    booking: Booking,
    payment: Payment?,
    errorMessage: String?,
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
        // Escrow info banner
        EscrowInfoBanner()

        // Order summary
        OrderSummaryCard(booking = booking)

        // Price breakdown
        PriceBreakdownCard(booking = booking)

        // Payment method
        PaymentMethodCard()

        // Error message if any
        if (errorMessage != null) {
            ErrorMessageCard(message = errorMessage)
        }
    }
}

// ─── Escrow Info Banner ───────────────────────────────────────────────────────

@Composable
private fun EscrowInfoBanner() {
    val isDark = isSystemInDarkTheme()
    val containerBg = if (isDark) Color(0xFF1A2B1A) else Color(0xFFE8F5E9)
    val iconColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val textColor = if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Thanh toán an toàn",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = iconColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tiền sẽ được hệ thống giữ an toàn. Chỉ chuyển cho thợ sau khi bạn xác nhận công việc hoàn thành.",
                    fontSize = 12.sp,
                    color = textColor,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ─── Order Summary Card ───────────────────────────────────────────────────────

@Composable
private fun OrderSummaryCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Chi tiết đơn hàng",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(
                icon = Icons.Outlined.Category,
                label = "Dịch vụ",
                value = booking.category.displayName
            )
            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(
                icon = Icons.Outlined.Description,
                label = "Mô tả",
                value = booking.description
            )
            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(
                icon = Icons.Outlined.LocationOn,
                label = "Địa chỉ",
                value = booking.address
            )

            booking.worker?.let { worker ->
                Spacer(modifier = Modifier.height(10.dp))
                InfoRow(
                    icon = Icons.Outlined.Person,
                    label = "Thợ thực hiện",
                    value = worker.fullName
                )
            }
        }
    }
}

// ─── Price Breakdown Card ─────────────────────────────────────────────────────

@Composable
private fun PriceBreakdownCard(booking: Booking) {
    val amount = booking.agreedPrice ?: 0.0
    val total = amount

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Chi tiết thanh toán",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            PriceRow(
                label = "Chi phí dịch vụ",
                value = formatCurrencyVnd(amount)
            )
            Spacer(modifier = Modifier.height(8.dp))

            PriceRow(
                label = "Phí nền tảng (${com.example.fixbid.core.utils.PaymentConstants.PLATFORM_FEE_LABEL})",
                value = "Đã bao gồm",
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tổng thanh toán",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatCurrencyVnd(total),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─── Payment Method Card ──────────────────────────────────────────────────────

@Composable
private fun PaymentMethodCard() {
    val isDark = isSystemInDarkTheme()
    val vnpayBg = if (isDark) Color(0xFF1A1A2E) else Color(0xFFF0F4FF)
    val selectedBorder = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Phương thức thanh toán",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            // VNPay option (selected)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 2.dp,
                        color = selectedBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(vnpayBg)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VNPay",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Thanh toán qua ví VNPay, ATM, Visa/Master",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Đã chọn",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hint: other methods
            Text(
                text = "Thêm phương thức thanh toán khác sẽ sớm được hỗ trợ",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// ─── Error Message Card ───────────────────────────────────────────────────────

@Composable
private fun ErrorMessageCard(message: String) {
    val isDark = isSystemInDarkTheme()
    val errorBg = if (isDark) Color(0xFF2D1010) else Color(0xFFFFEBEE)
    val errorColor = if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = errorBg)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = errorColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = errorColor,
                lineHeight = 18.sp
            )
        }
    }
}

// ─── Payment Success Content ──────────────────────────────────────────────────

@Composable
private fun PaymentSuccessContent(
    booking: Booking,
    payment: Payment?,
    contentPadding: PaddingValues,
    onContinue: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val successBg = if (isDark) Color(0xFF1A2B1A) else Color(0xFFE8F5E9)
    val successColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            )
            .padding(horizontal = 16.dp)
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(successBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = successColor,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Thanh toán thành công!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = successColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tiền đang được hệ thống giữ an toàn.\nThợ sẽ bắt đầu thực hiện công việc.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Payment details card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Thông tin giao dịch",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (payment != null) {
                    InfoRow(
                        icon = Icons.Outlined.Receipt,
                        label = "Mã giao dịch",
                        value = payment.transactionId ?: payment.id.take(12)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                InfoRow(
                    icon = Icons.Outlined.Payments,
                    label = "Số tiền",
                    value = formatCurrencyVnd(booking.agreedPrice ?: 0.0)
                )
                Spacer(modifier = Modifier.height(10.dp))

                InfoRow(
                    icon = Icons.Outlined.AccountBalance,
                    label = "Phương thức",
                    value = "VNPay"
                )
                Spacer(modifier = Modifier.height(10.dp))

                InfoRow(
                    icon = Icons.Outlined.Security,
                    label = "Trạng thái",
                    value = "Đang giữ tiền (Escrow)"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Escrow explanation card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Các bước tiếp theo",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                StepItem(step = "1", text = "Thợ nhận được thông báo và bắt đầu công việc")
                Spacer(modifier = Modifier.height(6.dp))
                StepItem(step = "2", text = "Thợ xác nhận hoàn thành khi xong")
                Spacer(modifier = Modifier.height(6.dp))
                StepItem(step = "3", text = "Bạn kiểm tra và xác nhận hoàn thành")
                Spacer(modifier = Modifier.height(6.dp))
                StepItem(step = "4", text = "Tiền được chuyển cho thợ")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                "Về trang chủ",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

// ─── Step Item ────────────────────────────────────────────────────────────────

@Composable
private fun StepItem(step: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}

// ─── Bottom Bar ───────────────────────────────────────────────────────────────

@Composable
private fun PaymentBottomBar(
    isProcessing: Boolean,
    onPayClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Button(
                onClick = onPayClick,
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Đang xử lý...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                } else {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Thanh toán ngay",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bạn sẽ được chuyển sang VNPay để hoàn tất thanh toán",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Shared Components ────────────────────────────────────────────────────────

@Composable
private fun InfoRow(
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun PriceRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
            fontWeight = FontWeight.Medium
        )
    }
}