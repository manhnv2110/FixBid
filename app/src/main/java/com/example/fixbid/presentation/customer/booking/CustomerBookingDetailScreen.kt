package com.example.fixbid.presentation.customer.booking

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.components.ScheduleDateTimePicker
import com.example.fixbid.core.components.StatusPill
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.core.utils.formatDateTimeVi
import com.example.fixbid.core.utils.formatRelativeTime
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.ui.theme.AccentGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CustomerBookingDetailScreen(
    onBackClick: () -> Unit,
    onNavigateToBids: (String) -> Unit,
    onNavigateToPayment: (String) -> Unit,
    onNavigateToCompletionConfirm: (String) -> Unit,
    onWalletClick: () -> Unit = {},
    viewModel: CustomerBookingDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddressPicker by remember { mutableStateOf(false) }
    var isFetchingMyLocation by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expandedCategoryMenu by remember { mutableStateOf(false) }
    // Reject-the-worker-quote dialog state. Only opens for QUOTED bookings,
    // collects an optional reason and forwards it to the use case.
    var showRejectQuoteDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onImagesSelected(uris)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            Toast.makeText(context, "Cần quyền truy cập vị trí để tự động điền địa chỉ", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isFetchingMyLocation = true
            val loc = viewModel.locator.getCurrentLocation()
            if (loc == null) {
                isFetchingMyLocation = false
                Toast.makeText(context, "Không lấy được vị trí. Hãy bật GPS và thử lại.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val resolved = viewModel.geocoder.reverseGeocode(loc.latitude, loc.longitude)
            viewModel.onAddressChange(
                resolved ?: "%.5f, %.5f".format(loc.latitude, loc.longitude),
                loc.latitude,
                loc.longitude
            )
            isFetchingMyLocation = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomerBookingDetailEvent.Toast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                CustomerBookingDetailEvent.BookingDeleted -> {
                    onBackClick()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppHeader(
                title = if (uiState.isEditing) "Chỉnh sửa yêu cầu" else "Chi tiết yêu cầu",
                onBackClick = {
                    if (uiState.isEditing) {
                        viewModel.toggleEditMode()
                    } else {
                        onBackClick()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = viewModel::loadBooking) {
                            Text("Thử lại")
                        }
                    }
                }
            }
            uiState.booking != null -> {
                val booking = uiState.booking!!
                val statusInfo = getStatusInfo(booking.status)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Refund banner — shown above the rest of the details when
                    // the worker has cancelled and the escrow has been refunded
                    // back into the customer's wallet. Predicate matches Req 8.1.
                    val payment = uiState.payment
                    if (
                        booking.status == BookingStatus.CANCELLED &&
                        payment?.escrowStatus == com.example.fixbid.domain.model.EscrowStatus.REFUNDED
                    ) {
                        WorkerCancelledRefundBanner(
                            refundAmount = payment.amount,
                            cancelReason = booking.cancelReason,
                            onWalletClick = onWalletClick
                        )
                    }

                    // Status summary card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(text = booking.category.displayName, color = MaterialTheme.colorScheme.primary)
                                StatusPill(text = statusInfo.label, color = statusInfo.color)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Mã đơn: #${booking.id.take(8).uppercase()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Đăng lúc: ${formatRelativeTime(booking.createdAt)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // Payment receipt — visible once the booking is fully
                    // completed and the escrow has been released. Pulls
                    // amount, fee and "received at" timestamp from the
                    // payments table so the customer has a clear receipt.
                    if (booking.status == BookingStatus.COMPLETED && uiState.payment != null) {
                        CompletedPaymentCard(payment = uiState.payment!!)
                    }

                    // Main info Card (View mode or Edit mode)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Chi tiết công việc",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.isEditing) {
                                // EDITING SERVICE CATEGORY
                                SectionLabel(icon = Icons.Outlined.Category, text = "Loại dịch vụ")
                                Spacer(modifier = Modifier.height(4.dp))
                                ExposedDropdownMenuBox(
                                    expanded = expandedCategoryMenu,
                                    onExpandedChange = { expandedCategoryMenu = !expandedCategoryMenu },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = uiState.editCategory.displayName,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCategoryMenu) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = fieldColors()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedCategoryMenu,
                                        onDismissRequest = { expandedCategoryMenu = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        ServiceCategory.values().forEach { category ->
                                            DropdownMenuItem(
                                                text = { Text(category.displayName, color = MaterialTheme.colorScheme.onSurface) },
                                                onClick = {
                                                    viewModel.onCategoryChange(category)
                                                    expandedCategoryMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                // EDITING DESCRIPTION
                                SectionLabel(icon = Icons.Outlined.Description, text = "Mô tả vấn đề")
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = uiState.editDescription,
                                    onValueChange = viewModel::onDescriptionChange,
                                    placeholder = { Text("Mô tả chi tiết vấn đề cần sửa chữa...") },
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = fieldColors(),
                                    maxLines = 4
                                )
                            } else {
                                // VIEWING DESCRIPTION
                                DetailRow(
                                    icon = Icons.Outlined.Description,
                                    label = "Mô tả công việc",
                                    value = booking.description
                                )
                            }
                        }
                    }

                    // Schedule & Location Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Thời gian & Địa điểm",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.isEditing) {
                                // EDITING TIME
                                SectionLabel(icon = Icons.Outlined.EventAvailable, text = "Thời gian hẹn")
                                Spacer(modifier = Modifier.height(4.dp))
                                ScheduleDateTimePicker(
                                    scheduledAtMillis = uiState.editScheduledAt,
                                    onScheduledAtChange = viewModel::onScheduledAtChange
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                // EDITING ADDRESS
                                SectionLabel(icon = Icons.Outlined.LocationOn, text = "Địa chỉ")
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AddressActionChip(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Outlined.MyLocation,
                                        label = "Vị trí hiện tại",
                                        onClick = {
                                            if (viewModel.locator.hasFineLocationPermission()) {
                                                scope.launch {
                                                    isFetchingMyLocation = true
                                                    val loc = viewModel.locator.getCurrentLocation()
                                                    if (loc == null) {
                                                        isFetchingMyLocation = false
                                                        Toast.makeText(context, "Không lấy được vị trí. Hãy bật GPS và thử lại.", Toast.LENGTH_SHORT).show()
                                                        return@launch
                                                    }
                                                    val resolved = viewModel.geocoder.reverseGeocode(loc.latitude, loc.longitude)
                                                    viewModel.onAddressChange(
                                                        resolved ?: "%.5f, %.5f".format(loc.latitude, loc.longitude),
                                                        loc.latitude,
                                                        loc.longitude
                                                    )
                                                    isFetchingMyLocation = false
                                                }
                                            } else {
                                                locationPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            }
                                        }
                                    )
                                    AddressActionChip(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Outlined.Map,
                                        label = "Chọn trên bản đồ",
                                        onClick = { showAddressPicker = true }
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = uiState.editAddress,
                                    onValueChange = { viewModel.onAddressChange(it, uiState.editLatitude, uiState.editLongitude) },
                                    placeholder = { Text("Số nhà, tên đường, khu vực...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = fieldColors(),
                                    maxLines = 2
                                )
                            } else {
                                // VIEWING TIME & LOCATION
                                DetailRow(
                                    icon = Icons.Outlined.Schedule,
                                    label = "Thời gian hẹn",
                                    value = formatDateTimeVi(booking.scheduledAt)
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                DetailRow(
                                    icon = Icons.Outlined.LocationOn,
                                    label = "Địa chỉ làm việc",
                                    value = booking.address
                                )
                            }
                        }
                    }

                    // Description Images Card
                    val descriptionImages = booking.descriptionImages ?: emptyList()
                    if (descriptionImages.isNotEmpty() || uiState.isEditing) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Hình ảnh đính kèm",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (uiState.isEditing) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Ảnh hiện tại & thêm mới (tối đa 5 ảnh)",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Existing images
                                        items(descriptionImages) { url ->
                                            Box {
                                                AsyncImage(
                                                    model = url,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.6f))
                                                        .clickable { viewModel.removeExistingImage(url) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Close,
                                                        contentDescription = "Xóa",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Newly added images
                                        items(uiState.editImageUris) { uri ->
                                            Box {
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.6f))
                                                        .clickable { viewModel.removeImage(uri) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Close,
                                                        contentDescription = "Xóa",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Add button
                                        val totalCount = descriptionImages.size + uiState.editImageUris.size
                                        if (totalCount < 5) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                        .clickable {
                                                            imagePickerLauncher.launch(
                                                                PickVisualMediaRequest(
                                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                                )
                                                            )
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.AddAPhoto,
                                                        contentDescription = "Thêm ảnh",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // View images only
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(descriptionImages) { url ->
                                            AsyncImage(
                                                model = url,
                                                contentDescription = "Ảnh công việc",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(100.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.outlineVariant,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Contact Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Thông tin khách hàng & Ghi chú",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.isEditing) {
                                SectionLabel(icon = Icons.Outlined.Person, text = "Tên liên hệ")
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = uiState.editFullName,
                                    onValueChange = viewModel::onFullNameChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = fieldColors(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                SectionLabel(icon = Icons.Outlined.Phone, text = "Số điện thoại")
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = uiState.editPhone,
                                    onValueChange = viewModel::onPhoneChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = fieldColors(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                SectionLabel(icon = Icons.Outlined.Notes, text = "Ghi chú thêm")
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = uiState.editNotes,
                                    onValueChange = viewModel::onNotesChange,
                                    placeholder = { Text("Ví dụ: Tầng, số căn hộ, lưu ý thiết bị...") },
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = fieldColors(),
                                    maxLines = 3
                                )
                            } else {
                                DetailRow(
                                    icon = Icons.Outlined.Person,
                                    label = "Họ và tên",
                                    value = uiState.editFullName.ifBlank { "Không có tên" }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                DetailRow(
                                    icon = Icons.Outlined.Phone,
                                    label = "Số điện thoại",
                                    value = uiState.editPhone.ifBlank { "Không có số điện thoại" }
                                )
                                if (uiState.editNotes.isNotBlank()) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    DetailRow(
                                        icon = Icons.Outlined.Notes,
                                        label = "Ghi chú cho thợ",
                                        value = uiState.editNotes
                                    )
                                }
                            }
                        }
                    }

                    // Assigned Worker Card (If any)
                    if (booking.worker != null && booking.agreedPrice != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Thợ phụ trách công việc",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = booking.worker.fullName.first().toString().uppercase(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = booking.worker.fullName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "SĐT: ${booking.worker.phoneNumber ?: "Chưa cập nhật"}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatCurrencyVnd(booking.agreedPrice),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons
                    if (uiState.isEditing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::toggleEditMode,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Huỷ bỏ", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    viewModel.saveChanges(
                                        imageResolver = { uri ->
                                            runCatching {
                                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                            }.getOrNull()
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !uiState.isSaving
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Lưu thay đổi", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Display control buttons based on booking status
                        when (booking.status) {
                            BookingStatus.BIDDING -> {
                                // For bidding stage: can edit, cancel, or delete
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { onNavigateToBids(booking.id) },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Outlined.Gavel, null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Xem danh sách báo giá từ thợ", fontWeight = FontWeight.Bold)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = viewModel::toggleEditMode,
                                            modifier = Modifier.weight(1f).height(50.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                        ) {
                                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Sửa thông tin", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                        Button(
                                            onClick = { showCancelDialog = true },
                                            modifier = Modifier.weight(1f).height(50.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                        ) {
                                            Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Huỷ yêu cầu", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { showDeleteDialog = true },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Xoá yêu cầu vĩnh viễn", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            BookingStatus.AWAITING_PAYMENT -> {
                                Button(
                                    onClick = { onNavigateToPayment(booking.id) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                                ) {
                                    Icon(Icons.Outlined.Payments, null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tiến hành thanh toán ngay", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                            BookingStatus.QUOTED -> {
                                // Worker has sent a price quote — surface the
                                // proposed price prominently and give the
                                // customer two actions: accept (→ payment) or
                                // reject with optional reason (→ back to PENDING
                                // so the worker can re-quote or decline).
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    WorkerQuoteCard(booking = booking)

                                    Button(
                                        onClick = viewModel::acceptQuote,
                                        enabled = !uiState.isRespondingQuote,
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                                    ) {
                                        if (uiState.isRespondingQuote) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Chấp nhận báo giá", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showRejectQuoteDialog = true },
                                            enabled = !uiState.isRespondingQuote,
                                            modifier = Modifier.weight(1f).height(50.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Yêu cầu báo lại", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { showCancelDialog = true },
                                            enabled = !uiState.isRespondingQuote,
                                            modifier = Modifier.weight(1f).height(50.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Huỷ yêu cầu", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                            BookingStatus.PENDING_COMPLETION -> {
                                Button(
                                    onClick = { onNavigateToCompletionConfirm(booking.id) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Xác nhận thợ hoàn thành", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                            BookingStatus.PENDING, BookingStatus.CONFIRMED -> {
                                // Before the worker starts, the customer may still cancel.
                                OutlinedButton(
                                    onClick = { showCancelDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Huỷ yêu cầu", fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                // For other states (cancelled, in progress, completed) we don't show booking actions
                            }
                        }
                    }
                }
            }
        }

        // Address picker sheet
        if (showAddressPicker && uiState.booking != null) {
            AddressPickerSheet(
                initialLatitude = uiState.editLatitude,
                initialLongitude = uiState.editLongitude,
                initialAddress = uiState.editAddress,
                locationRepository = viewModel.locator,
                geocoderRepository = viewModel.geocoder,
                onDismiss = { showAddressPicker = false },
                onConfirm = { lat, lng, resolved ->
                    viewModel.onAddressChange(resolved, lat, lng)
                    showAddressPicker = false
                }
            )
        }

        // Cancel confirmation dialog with reason capture
        if (showCancelDialog) {
            val presetReasons = listOf(
                "Đổi lịch / không còn nhu cầu",
                "Tìm được thợ khác",
                "Giá chưa phù hợp",
                "Đặt nhầm dịch vụ"
            )
            AlertDialog(
                onDismissRequest = {
                    if (!uiState.isCancelling) {
                        showCancelDialog = false
                        cancelReason = ""
                    }
                },
                title = { Text("Huỷ yêu cầu dịch vụ?", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Cho chúng tôi biết lý do bạn huỷ (không bắt buộc):",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetReasons.forEach { reason ->
                                FilterChip(
                                    selected = cancelReason == reason,
                                    onClick = { cancelReason = reason },
                                    label = { Text(reason, fontSize = 12.sp) }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = cancelReason,
                            onValueChange = { cancelReason = it },
                            placeholder = { Text("Hoặc nhập lý do khác...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(10.dp),
                            enabled = !uiState.isCancelling
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !uiState.isCancelling,
                        onClick = {
                            viewModel.cancelBooking(cancelReason)
                            showCancelDialog = false
                            cancelReason = ""
                        }
                    ) {
                        if (uiState.isCancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("Đồng ý huỷ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !uiState.isCancelling,
                        onClick = {
                            showCancelDialog = false
                            cancelReason = ""
                        }
                    ) {
                        Text("Không")
                    }
                }
            )
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Xoá yêu cầu đặt lịch?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                text = { Text("Hành động này sẽ xoá hoàn toàn yêu cầu đặt lịch và các báo giá liên quan khỏi hệ thống và không thể hoàn tác.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBooking()
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Xác nhận xoá", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Huỷ")
                    }
                }
            )
        }

        // Reject-the-worker-quote dialog. Reuses the preset-chip pattern from
        // the cancel dialog but submits to viewModel.rejectQuote so the booking
        // moves back to PENDING with the reason persisted.
        if (showRejectQuoteDialog) {
            RejectQuoteDialog(
                isSubmitting = uiState.isRespondingQuote,
                onDismiss = { showRejectQuoteDialog = false },
                onConfirm = { reason ->
                    showRejectQuoteDialog = false
                    viewModel.rejectQuote(reason)
                }
            )
        }
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary
)

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

@Composable
private fun AddressActionChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class StatusInfo(val label: String, val color: Color)

@Composable
private fun getStatusInfo(status: BookingStatus): StatusInfo =
    StatusInfo(
        com.example.fixbid.ui.theme.statusLabel(status),
        com.example.fixbid.ui.theme.statusColor(status)
    )

// ─── Worker-cancelled refund banner ──────────────────────────────────────────

@Composable
private fun WorkerCancelledRefundBanner(
    refundAmount: Double,
    cancelReason: String?,
    onWalletClick: () -> Unit
) {
    // Distinct error-tinted card matching the worker side's "Bạn đã hủy đơn này"
    // banner (JobDetailScreen.WorkerCancelledBanner) so cancel-and-refunded
    // bookings read the same on both sides. Predicate enforced by caller.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Thợ đã hủy đơn",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Customer is refunded the full amount they paid (including
            // platform_fee) per fn_refund_escrow_to_customer policy.
            Text(
                text = "Số tiền đã hoàn: ${formatCurrencyVnd(refundAmount)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            // Reason is technically nullable on the model (legacy data), but
            // any booking cancelled via WorkerCancelBookingUseCase always has
            // one. Hide the line entirely rather than showing "Lý do: null".
            if (!cancelReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lý do: $cancelReason",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    lineHeight = 19.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onWalletClick,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Xem ví", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ─── Completed payment receipt ───────────────────────────────────────────────

@Composable
private fun CompletedPaymentCard(payment: com.example.fixbid.domain.model.Payment) {
    val released = payment.escrowStatus == com.example.fixbid.domain.model.EscrowStatus.RELEASED ||
        payment.status == com.example.fixbid.domain.model.PaymentStatus.COMPLETED

    val accent = if (released)
        com.example.fixbid.ui.theme.AccentGreen
    else
        com.example.fixbid.ui.theme.StatusColorsTheme.current.pending

    val statusLabel = if (released)
        "Đã thanh toán cho thợ"
    else
        "Đang xử lý thanh toán"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Receipt,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hoá đơn thanh toán",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        color = accent
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            ReceiptLine(
                label = "Bạn đã thanh toán",
                value = formatCurrencyVnd(payment.amount)
            )
            Spacer(modifier = Modifier.height(4.dp))
            ReceiptLine(
                label = "Phí nền tảng (${com.example.fixbid.core.utils.PaymentConstants.PLATFORM_FEE_LABEL})",
                value = formatCurrencyVnd(payment.platformFee)
            )
            Spacer(modifier = Modifier.height(4.dp))
            ReceiptLine(
                label = "Thợ nhận được",
                value = formatCurrencyVnd(payment.workerReceives)
            )

            if (payment.releasedAt != null && payment.releasedAt > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chuyển cho thợ lúc ${formatRelativeTime(payment.releasedAt)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReceiptLine(label: String, value: String) {
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


// ─── Direct booking quote review card ──────────────────────────────────────

/**
 * Highlight card the customer sees when a direct booking is in QUOTED state.
 * Surfaces the worker name, the proposed price (centerpiece), the optional
 * note from the worker and the proposed duration so the customer has the full
 * context before tapping "Chấp nhận báo giá" or "Yêu cầu báo lại".
 */
@Composable
private fun WorkerQuoteCard(booking: com.example.fixbid.domain.model.Booking) {
    val price = booking.quotedPrice ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentGreen.copy(alpha = 0.10f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.RequestQuote,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Báo giá từ thợ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = booking.worker?.fullName ?: "Thợ",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatCurrencyVnd(price),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
            }
            booking.quoteEstimatedDurationHours?.takeIf { it > 0 }?.let { hours ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Thời gian dự kiến: $hours giờ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            booking.quoteMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RejectQuoteDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val presetReasons = listOf(
        "Giá cao hơn ngân sách",
        "Cần thợ báo giá thấp hơn",
        "Muốn thay đổi phạm vi công việc",
        "Cần thảo luận thêm với thợ"
    )

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Từ chối báo giá?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Cho thợ biết lý do để họ điều chỉnh báo giá phù hợp hơn:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("Hoặc nhập lý do khác (tùy chọn)…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = { onConfirm(reason.trim().takeIf { it.isNotBlank() }) }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Từ chối báo giá", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text("Đóng") }
        }
    )
}
