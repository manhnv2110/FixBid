package com.example.fixbid.presentation.worker.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WorkHistory
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.ui.theme.AccentGreen
import com.example.fixbid.ui.theme.StatusColorsTheme

/**
 * Worker professional profile editor.
 *
 * Layout:
 *  - Hero card at the top: avatar with the worker's initial, name + verified
 *    badge, and three stat tiles (rating, reviews, jobs done) so the worker
 *    sees their public-facing reputation while editing.
 *  - Section cards with a leading icon badge and clear vertical rhythm.
 *  - Sticky save bar at the bottom so the primary action is always reachable.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkerProfileEditScreen(
    onBackClick: () -> Unit = {},
    viewModel: WorkerProfileEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) viewModel.uploadAvatar(bytes)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkerProfileEditEvent.Toast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                WorkerProfileEditEvent.Saved -> onBackClick()
            }
        }
    }

    Scaffold(
        topBar = { AppHeader(title = "Hồ sơ nghề nghiệp", onBackClick = onBackClick) },
        bottomBar = {
            if (!uiState.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        Button(
                            onClick = viewModel::save,
                            enabled = !uiState.isSaving,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Lưu hồ sơ", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp
                )
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileHeroCard(
                uiState = uiState,
                onAvatarClick = { imagePickerLauncher.launch("image/*") }
            )

            ProfileSectionCard(
                icon = Icons.Outlined.Description,
                title = "Giới thiệu bản thân",
                hint = "Giúp khách hàng hiểu thế mạnh của bạn"
            ) {
                OutlinedTextField(
                    value = uiState.bio,
                    onValueChange = viewModel::onBioChange,
                    placeholder = {
                        Text(
                            "Mô tả kinh nghiệm, thế mạnh, phong cách làm việc của bạn...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${uiState.bio.length}/500 ký tự",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            ProfileSectionCard(
                icon = Icons.Outlined.Build,
                title = "Kỹ năng chuyên môn",
                hint = "Yêu cầu sẽ được gợi ý theo kỹ năng đã chọn",
                required = true
            ) {
                if (uiState.selectedSkills.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = "Chọn ít nhất 1 kỹ năng để có thể nhận việc",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServiceCategory.entries.forEach { category ->
                        val selected = category in uiState.selectedSkills
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleSkill(category) },
                            label = {
                                Text(
                                    category.displayName,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            ProfileSectionCard(
                icon = Icons.Outlined.Paid,
                title = "Kinh nghiệm & giá",
                hint = "Đặt giá hợp lý so với mặt bằng thị trường để dễ trúng thầu"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.experienceYears,
                        onValueChange = viewModel::onExperienceChange,
                        label = { Text("Năm kinh nghiệm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.WorkHistory,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.pricePerHour,
                    onValueChange = viewModel::onPriceChange,
                    label = { Text("Giá cơ bản theo giờ") },
                    placeholder = { Text("VD: 100000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Paid,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    suffix = { Text("đ/giờ", fontSize = 12.sp) }
                )
            }

            ProfileSectionCard(
                icon = Icons.Outlined.LocationOn,
                title = "Khu vực hoạt động",
                hint = "Khách trong khu vực này sẽ ưu tiên thấy bạn"
            ) {
                OutlinedTextField(
                    value = uiState.location,
                    onValueChange = viewModel::onLocationChange,
                    placeholder = { Text("VD: Quận Cầu Giấy, Hà Nội") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            if (uiState.errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

// ─── Hero card ───────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeroCard(
    uiState: WorkerProfileEditUiState,
    onAvatarClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar with camera edit badge
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))
                            .clickable { onAvatarClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.avatarUrl != null) {
                            AsyncImage(
                                model = uiState.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = "T",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 26.sp
                            )
                        }
                        // Upload loading overlay
                        if (uiState.isUploadingAvatar) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    // Camera badge
                    if (!uiState.isUploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary)
                                .clickable { onAvatarClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Chỉnh sửa ảnh đại diện",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hồ sơ nghề nghiệp",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (uiState.selectedSkills.isNotEmpty())
                            uiState.selectedSkills.first().displayName +
                                if (uiState.selectedSkills.size > 1)
                                    " +${uiState.selectedSkills.size - 1}"
                                else ""
                        else "Chưa có kỹ năng",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    if (uiState.identityVerified) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Đã xác minh",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        Text(
                            text = "Chưa xác minh danh tính",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Stat strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroStat(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Star,
                        iconTint = StatusColorsTheme.current.rating,
                        value = if (uiState.averageRating > 0)
                            "%.1f".format(uiState.averageRating) else "—",
                        label = "Đánh giá"
                    )
                    HeroDivider()
                    HeroStat(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Description,
                        iconTint = MaterialTheme.colorScheme.onPrimary,
                        value = "${uiState.totalReviews}",
                        label = "Lượt đánh giá"
                    )
                    HeroDivider()
                    HeroStat(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.TaskAlt,
                        iconTint = AccentGreen,
                        value = "${uiState.totalJobsDone}",
                        label = "Việc đã làm"
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    value: String,
    label: String
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            maxLines = 1
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun HeroDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f))
    )
}

// ─── Section card ────────────────────────────────────────────────────────────

@Composable
private fun ProfileSectionCard(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    required: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (required) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "*",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (hint != null) {
                        Text(
                            text = hint,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
