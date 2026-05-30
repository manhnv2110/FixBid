package com.example.fixbid.presentation.worker.profile

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.components.StarRatingBar
import com.example.fixbid.domain.model.ServiceCategory

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkerProfileEditScreen(
    onBackClick: () -> Unit = {},
    viewModel: WorkerProfileEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkerProfileEditEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
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
                            modifier = Modifier.fillMaxWidth().height(50.dp),
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
            // Read-only stats summary
            StatsSummary(uiState)

            // Bio
            SectionCard(title = "Giới thiệu bản thân") {
                OutlinedTextField(
                    value = uiState.bio,
                    onValueChange = viewModel::onBioChange,
                    placeholder = { Text("Mô tả kinh nghiệm, thế mạnh, phong cách làm việc của bạn...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Skills
            SectionCard(title = "Kỹ năng chuyên môn *") {
                Text(
                    text = "Chọn các dịch vụ bạn có thể nhận. Yêu cầu sẽ được gợi ý theo kỹ năng này.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServiceCategory.entries.forEach { category ->
                        val selected = category in uiState.selectedSkills
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleSkill(category) },
                            label = { Text(category.displayName) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // Experience + price
            SectionCard(title = "Kinh nghiệm & giá") {
                OutlinedTextField(
                    value = uiState.experienceYears,
                    onValueChange = viewModel::onExperienceChange,
                    label = { Text("Số năm kinh nghiệm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.pricePerHour,
                    onValueChange = viewModel::onPriceChange,
                    label = { Text("Giá cơ bản theo giờ (đ)") },
                    placeholder = { Text("VD: 100000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    suffix = { Text("đ/giờ") }
                )
            }

            // Location
            SectionCard(title = "Khu vực hoạt động") {
                OutlinedTextField(
                    value = uiState.location,
                    onValueChange = viewModel::onLocationChange,
                    placeholder = { Text("VD: Quận Cầu Giấy, Hà Nội") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatsSummary(uiState: WorkerProfileEditUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (uiState.identityVerified) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Đã xác minh danh tính", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                StatCell(
                    modifier = Modifier.weight(1f),
                    icon = { StarRatingBar(rating = uiState.averageRating, starSize = 14.dp) },
                    value = if (uiState.averageRating > 0) "%.1f".format(uiState.averageRating) else "Mới",
                    label = "${uiState.totalReviews} đánh giá"
                )
                Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                StatCell(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    value = "${uiState.totalJobsDone}",
                    label = "Việc hoàn thành"
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    value: String,
    label: String
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
