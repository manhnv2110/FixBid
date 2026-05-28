package com.example.fixbid.presentation.customer.booking

import android.Manifest
import android.net.Uri
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.fixbid.core.components.ScheduleDateTimePicker
import com.example.fixbid.domain.model.ServiceCategory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(
    initialCategoryName: String?,
    onBackClick: () -> Unit,
    onSubmitSuccess: () -> Unit,
    viewModel: BookingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is BookingUiState.Success) {
            viewModel.resetState()
            onSubmitSuccess()
        }
    }

    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember {
        mutableStateOf(
            ServiceCategory.values().find { it.name == initialCategoryName }
                ?: ServiceCategory.OTHER
        )
    }

    var description by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var addressLatitude by remember { mutableStateOf<Double?>(null) }
    var addressLongitude by remember { mutableStateOf<Double?>(null) }
    var scheduledAtMillis by remember { mutableStateOf<Long?>(null) }
    var showAddressPicker by remember { mutableStateOf(false) }
    var isFetchingMyLocation by remember { mutableStateOf(false) }
    
    val initialFullName by viewModel.initialFullName.collectAsState()
    val initialPhone by viewModel.initialPhone.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val descriptionImageUris by viewModel.descriptionImageUris.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onDescriptionImagesSelected(uris)
    }

    // Fired after the user grants ACCESS_FINE/COARSE_LOCATION; immediately fetches a
    // fix and reverse-geocodes it.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            Toast.makeText(
                context,
                "Cần quyền truy cập vị trí để tự động điền địa chỉ",
                Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isFetchingMyLocation = true
            val loc = viewModel.locator.getCurrentLocation()
            if (loc == null) {
                isFetchingMyLocation = false
                Toast.makeText(
                    context,
                    "Không lấy được vị trí. Hãy bật GPS và thử lại.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            addressLatitude = loc.latitude
            addressLongitude = loc.longitude
            val resolved = viewModel.geocoder.reverseGeocode(loc.latitude, loc.longitude)
            address = resolved
                ?: "%.5f, %.5f".format(loc.latitude, loc.longitude)
            isFetchingMyLocation = false
        }
    }
    
    var fullName by remember(initialFullName) { mutableStateOf(initialFullName) }
    var phoneNumber by remember(initialPhone) { mutableStateOf(initialPhone) }
    var notes by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Đặt lịch dịch vụ",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category selection card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel(icon = Icons.Outlined.Category, text = "Loại dịch vụ")
                    Spacer(modifier = Modifier.height(10.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedCategory.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            ServiceCategory.values().forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.displayName, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        selectedCategory = category
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Description card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel(icon = Icons.Outlined.Description, text = "Mô tả công việc")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Mô tả chi tiết vấn đề cần sửa chữa...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors(),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Ảnh mô tả công việc
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ảnh mô tả (tùy chọn, tối đa 5)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Nút thêm ảnh
                        if (descriptionImageUris.size < 5) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            imagePickerLauncher.launch(
                                                PickVisualMediaRequest(
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
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Thêm ảnh",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        items(descriptionImageUris, key = { it.toString() }) { uri ->
                            Box {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .clickable { viewModel.removeDescriptionImage(uri) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Xóa",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (descriptionImageUris.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${descriptionImageUris.size}/5 ảnh",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Schedule card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel(
                        icon = Icons.Outlined.EventAvailable,
                        text = if (selectedCategory == ServiceCategory.CLEANING)
                            "Lịch dọn dẹp"
                        else "Thời gian hẹn"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ScheduleDateTimePicker(
                        scheduledAtMillis = scheduledAtMillis,
                        onScheduledAtChange = { scheduledAtMillis = it }
                    )
                    if (selectedCategory == ServiceCategory.CLEANING) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Chọn ngày và khung giờ bạn muốn nhân viên có mặt. Thợ sẽ đến đúng giờ đã hẹn.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Address card — typed input + GPS auto-fill + map picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel(icon = Icons.Outlined.LocationOn, text = "Địa chỉ")
                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick actions: GPS + map picker. Each is a tap-friendly chip
                    // matching the look of Material 3 assist chips.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AddressActionChip(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.MyLocation,
                            label = "Vị trí hiện tại",
                            loading = isFetchingMyLocation,
                            onClick = {
                                if (viewModel.locator.hasFineLocationPermission()) {
                                    scope.launch {
                                        isFetchingMyLocation = true
                                        val loc = viewModel.locator.getCurrentLocation()
                                        if (loc == null) {
                                            isFetchingMyLocation = false
                                            Toast.makeText(
                                                context,
                                                "Không lấy được vị trí. Hãy bật GPS và thử lại.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@launch
                                        }
                                        addressLatitude = loc.latitude
                                        addressLongitude = loc.longitude
                                        val resolved = viewModel.geocoder.reverseGeocode(
                                            loc.latitude, loc.longitude
                                        )
                                        address = resolved
                                            ?: "%.5f, %.5f".format(loc.latitude, loc.longitude)
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

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = address,
                        onValueChange = { newValue ->
                            address = newValue
                            // Manual edits invalidate the previously captured pin.
                            if (addressLatitude != null || addressLongitude != null) {
                                addressLatitude = null
                                addressLongitude = null
                            }
                        },
                        placeholder = {
                            Text(
                                "Số nhà, đường, phường/xã, quận/huyện",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors(),
                        maxLines = 3,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.LocationOn,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    // Pin confirmation badge — surfaces that we have precise coords so
                    // the customer feels confident the worker will land at the door.
                    if (addressLatitude != null && addressLongitude != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .clickable { showAddressPicker = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.PinDrop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Đã ghim vị trí trên bản đồ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "%.5f, %.5f".format(
                                        addressLatitude!!,
                                        addressLongitude!!
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Đổi",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Contact info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel(icon = Icons.Outlined.ContactPhone, text = "Thông tin liên hệ")
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Họ và tên") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Số điện thoại") },
                        placeholder = { Text("0xxx xxx xxx") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.Phone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    )
                }
            }

            // Notes card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel(icon = Icons.Outlined.Notes, text = "Ghi chú thêm (tuỳ chọn)")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = {
                            Text(
                                "Ví dụ: Số căn hộ, tầng, tình trạng thiết bị...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors(),
                        maxLines = 3
                    )
                }
            }

            // Info banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sau khi đặt lịch, các thợ sẽ gửi báo giá cho bạn. Bạn có thể so sánh và chọn thợ phù hợp nhất.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 18.sp
                    )
                }
            }

            // Error message
            if (uiState is BookingUiState.Error) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = (uiState as BookingUiState.Error).message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Submit button
            val requiresSchedule = selectedCategory == ServiceCategory.CLEANING
            val canSubmit = uiState !is BookingUiState.Loading &&
                    description.isNotBlank() &&
                    address.isNotBlank() &&
                    phoneNumber.isNotBlank() &&
                    fullName.isNotBlank() &&
                    (!requiresSchedule || scheduledAtMillis != null)

            Button(
                onClick = {
                    viewModel.createBooking(
                        category = selectedCategory,
                        description = description,
                        address = address,
                        phoneNumber = phoneNumber,
                        fullName = fullName,
                        notes = notes,
                        scheduledAtMillis = scheduledAtMillis
                            ?: System.currentTimeMillis(),
                        latitude = addressLatitude,
                        longitude = addressLongitude,
                        imageResolver = { uri ->
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            }.getOrNull()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = canSubmit
            ) {
                if (uiState is BookingUiState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Đang xử lý...", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Outlined.Send, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đặt lịch ngay", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Address picker bottom sheet (hosted inside the Scaffold so the system bars
        // adjust correctly with the IME).
        if (showAddressPicker) {
            AddressPickerSheet(
                initialLatitude = addressLatitude,
                initialLongitude = addressLongitude,
                initialAddress = address,
                locationRepository = viewModel.locator,
                geocoderRepository = viewModel.geocoder,
                onDismiss = { showAddressPicker = false },
                onConfirm = { lat, lng, resolved ->
                    addressLatitude = lat
                    addressLongitude = lng
                    address = resolved
                    showAddressPicker = false
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
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
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

/**
 * Tonal action chip used by the address card. Mirrors the look of Material 3 assist
 * chips but expands to fill the available row width so the two actions sit on a tidy
 * 50/50 grid.
 */
@Composable
private fun AddressActionChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
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
