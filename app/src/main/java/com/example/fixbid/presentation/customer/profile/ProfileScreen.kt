package com.example.fixbid.presentation.customer.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.fixbid.core.components.PrimaryTopBar
import com.example.fixbid.domain.model.UserRole

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onNotificationSettingsClick: () -> Unit = {},
    onWorkerProfileClick: (() -> Unit)? = null,
    onWalletClick: (() -> Unit)? = null,
    onHelpSupportClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState(initial = "system")
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState(initial = false)
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    // Hiển thị Toast khi có thông báo thành công
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) viewModel.uploadAvatar(bytes)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        PrimaryTopBar(title = "Hồ sơ cá nhân")

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.user == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Không thể tải thông tin", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = viewModel::loadProfile) {
                            Text("Thử lại", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            else -> {
                val user = uiState.user!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar + Name card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar with edit button
                            Box(
                                modifier = Modifier.size(88.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                // Avatar circle
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .clickable { imagePickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (user.avatarUrl != null) {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(user.avatarUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        ) {
                                            when (painter.state) {
                                                is AsyncImagePainter.State.Loading -> {
                                                    CircularProgressIndicator(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp).align(Alignment.Center),
                                                        strokeWidth = 2.dp
                                                    )
                                                }
                                                is AsyncImagePainter.State.Error -> {
                                                    Text(
                                                        text = user.fullName.firstOrNull()?.uppercase() ?: "?",
                                                        fontSize = 32.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.align(Alignment.Center)
                                                    )
                                                }
                                                else -> SubcomposeAsyncImageContent()
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = user.fullName.firstOrNull()?.uppercase() ?: "?",
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    // Upload overlay
                                    if (uiState.isUploadingAvatar) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(28.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                    }
                                }
                                // Camera badge
                                if (!uiState.isUploadingAvatar) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable { imagePickerLauncher.launch("image/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CameraAlt,
                                            contentDescription = "Chỉnh sửa ảnh đại diện",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = user.fullName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Role badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = user.role.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Info card (view mode)
                    if (!uiState.isEditing) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Thông tin cá nhân",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                ProfileInfoRow(
                                    icon = Icons.Outlined.Email,
                                    label = "Email",
                                    value = user.email
                                )

                                ProfileInfoRow(
                                    icon = Icons.Outlined.Phone,
                                    label = "Số điện thoại",
                                    value = user.phoneNumber ?: "Chưa cập nhật"
                                )

                                ProfileInfoRow(
                                    icon = Icons.Outlined.Person,
                                    label = "Họ và tên",
                                    value = user.fullName
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = viewModel::startEditing,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Chỉnh sửa hồ sơ", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Edit mode
                    if (uiState.isEditing) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Chỉnh sửa thông tin",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                OutlinedTextField(
                                    value = uiState.editFullName,
                                    onValueChange = viewModel::onFullNameChange,
                                    label = { Text("Họ và tên") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    singleLine = true,
                                    enabled = !uiState.isSaving
                                )

                                OutlinedTextField(
                                    value = uiState.editPhone,
                                    onValueChange = viewModel::onPhoneChange,
                                    label = { Text("Số điện thoại") },
                                    placeholder = { Text("+84...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    singleLine = true,
                                    enabled = !uiState.isSaving
                                )

                                // Email (read-only)
                                OutlinedTextField(
                                    value = user.email,
                                    onValueChange = {},
                                    label = { Text("Email") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    enabled = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                )

                                if (uiState.errorMessage != null) {
                                    Text(
                                        text = uiState.errorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 13.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = viewModel::cancelEditing,
                                        modifier = Modifier.weight(1f),
                                        enabled = !uiState.isSaving
                                    ) {
                                        Text("Huỷ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Button(
                                        onClick = viewModel::saveProfile,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        enabled = !uiState.isSaving
                                    ) {
                                        if (uiState.isSaving) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Lưu", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Menu items
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            if (onWorkerProfileClick != null && user.role == UserRole.WORKER) {
                                ProfileMenuItem(
                                    icon = Icons.Outlined.Work,
                                    title = "Hồ sơ nghề nghiệp",
                                    subtitle = "Kỹ năng, kinh nghiệm, giá dịch vụ",
                                    onClick = onWorkerProfileClick
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                            if (onWalletClick != null && user.role != UserRole.WORKER) {
                                ProfileMenuItem(
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    title = "Ví của tôi",
                                    subtitle = "Số dư & lịch sử giao dịch",
                                    onClick = onWalletClick
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                            ProfileMenuItem(
                                icon = Icons.Outlined.Payment,
                                title = "Thanh toán",
                                subtitle = "Quản lý phương thức thanh toán"
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            ThemeSelectorMenuItem(
                                currentTheme = currentTheme,
                                onThemeChange = viewModel::saveTheme
                            )
                            // Material You — only show on devices that
                            // support it (Android 12+). The flag is persisted
                            // either way, so a user upgrading from Android 11
                            // → 12 won't have to re-enable.
                            if (com.example.fixbid.ui.theme.SupportsDynamicColor) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                                MaterialYouMenuItem(
                                    enabled = dynamicColorEnabled,
                                    onToggle = viewModel::setDynamicColorEnabled
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            ProfileMenuItem(
                                icon = Icons.Outlined.Notifications,
                                title = "Thông báo",
                                subtitle = "Cài đặt thông báo",
                                onClick = onNotificationSettingsClick
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            ProfileMenuItem(
                                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                title = "Trợ giúp & Hỗ trợ",
                                subtitle = "FAQ, liên hệ hỗ trợ",
                                onClick = onHelpSupportClick
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            ProfileMenuItem(
                                icon = Icons.Outlined.Info,
                                title = "Về FixBid",
                                subtitle = "Phiên bản 1.0.0"
                            )
                        }
                    }

                    // Sign out button
                    Button(
                        onClick = { viewModel.signOut(onSignOut) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đăng xuất",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Only advertise navigation when there is somewhere to go — items without an
        // onClick used to still show a chevron and ripple, implying a dead link.
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ThemeSelectorMenuItem(
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val themeLabel = when (currentTheme) {
        "light" -> "Sáng"
        "dark" -> "Tối"
        else -> "Theo thiết bị"
    }
    
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Giao diện ứng dụng",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Chế độ sáng, tối hoặc theo thiết bị",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = themeLabel,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text("Sáng", color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = { Icon(Icons.Outlined.LightMode, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onThemeChange("light")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Tối", color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = { Icon(Icons.Outlined.DarkMode, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onThemeChange("dark")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Theo thiết bị", color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = { Icon(Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onThemeChange("system")
                    expanded = false
                }
            )
        }
    }
}

/**
 * Material You toggle — only rendered when the device supports dynamic colors
 * (Android 12+). Tapping the row OR the trailing switch flips the preference;
 * the theme recomposes immediately because [com.example.fixbid.MainActivity]
 * collects the flag as Compose state.
 */
@Composable
private fun MaterialYouMenuItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Màu theo hình nền",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (enabled) "Đang đồng bộ với hình nền thiết bị"
                else "Tắt — dùng màu thương hiệu mặc định",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}
