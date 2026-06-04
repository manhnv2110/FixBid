package com.example.fixbid.presentation.worker.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.utils.toFormattedDate
import com.example.fixbid.ui.theme.AccentGreen
import com.example.fixbid.ui.theme.StatusColorsTheme

/**
 * Worker identity verification screen.
 *
 * Three high-level states drive the layout:
 *  - VERIFIED → success card, no upload form.
 *  - PENDING → "đang xét duyệt" banner with the previously submitted documents
 *    visible in read-only state so the worker sees what's under review.
 *  - NOT_SUBMITTED → full upload flow with a 3-step process explainer,
 *    document slots (front of ID, back of ID, selfie holding the ID), basic
 *    identity fields, and a sticky submit button at the bottom.
 *
 * The "submit" action records the request locally for now; when a real
 * verification endpoint exists, swap the ViewModel's submit logic only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerVerifyIdentityScreen(
    onBackClick: () -> Unit = {},
    viewModel: WorkerVerifyIdentityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VerifyIdentityEvent.Toast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                VerifyIdentityEvent.Submitted -> Unit /* keep user on screen */
            }
        }
    }

    Scaffold(
        topBar = { AppHeader(title = "Xác minh danh tính", onBackClick = onBackClick) },
        bottomBar = {
            if (!uiState.isLoading && uiState.status == VerificationStatus.NOT_SUBMITTED) {
                SubmitBar(
                    enabled = uiState.canSubmit,
                    isSubmitting = uiState.isSubmitting,
                    onSubmit = viewModel::submit
                )
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
            StatusBanner(state = uiState)

            when (uiState.status) {
                VerificationStatus.VERIFIED -> {
                    SecurityNote()
                }
                VerificationStatus.PENDING -> {
                    ReadOnlyDocumentsSummary(state = uiState)
                    SecurityNote()
                }
                VerificationStatus.NOT_SUBMITTED -> {
                    StepsExplainer()
                    DocumentsSection(
                        frontUri = uiState.frontUri,
                        backUri = uiState.backUri,
                        selfieUri = uiState.selfieUri,
                        onFrontSelected = viewModel::onFrontSelected,
                        onBackSelected = viewModel::onBackSelected,
                        onSelfieSelected = viewModel::onSelfieSelected
                    )
                    PersonalInfoSection(
                        fullName = uiState.fullName,
                        idNumber = uiState.idNumber,
                        onFullNameChange = viewModel::onFullNameChange,
                        onIdNumberChange = viewModel::onIdNumberChange
                    )
                    SecurityNote()

                    uiState.errorMessage?.let { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Status banner ───────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(state: VerifyIdentityUiState) {
    val (bgColor, fgColor, icon, title, subtitle) = when (state.status) {
        VerificationStatus.VERIFIED -> Quintuple(
            AccentGreen,
            Color.White,
            Icons.Filled.Verified,
            "Đã xác minh danh tính",
            "Bạn đã hoàn tất xác minh — khách hàng sẽ thấy huy hiệu xác minh trên hồ sơ."
        )
        VerificationStatus.PENDING -> Quintuple(
            StatusColorsTheme.current.pending,
            Color.White,
            Icons.Outlined.HourglassTop,
            "Đang xét duyệt",
            state.submittedAt?.let {
                "Gửi lúc ${it.toFormattedDate("HH:mm dd/MM/yyyy")} • thường mất 1-2 ngày làm việc"
            } ?: "Yêu cầu xác minh của bạn đang được đội ngũ xét duyệt"
        )
        VerificationStatus.NOT_SUBMITTED -> Quintuple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            Icons.Outlined.Badge,
            "Xác minh để được tin tưởng hơn",
            "Tăng tới 30% lượt được khách chọn khi hồ sơ có huy hiệu xác minh."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(fgColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = fgColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = fgColor.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private data class Quintuple<A, B, C, D, E>(
    val a: A, val b: B, val c: C, val d: D, val e: E
)

// ─── Steps explainer ─────────────────────────────────────────────────────────

@Composable
private fun StepsExplainer() {
    SectionCard(
        icon = Icons.Outlined.Info,
        title = "Quy trình xác minh"
    ) {
        StepRow(
            number = 1,
            title = "Tải lên giấy tờ",
            subtitle = "Ảnh CCCD/CMND mặt trước, mặt sau và ảnh selfie cầm giấy tờ"
        )
        Spacer(Modifier.height(10.dp))
        StepRow(
            number = 2,
            title = "Đội ngũ xét duyệt",
            subtitle = "Thường hoàn tất trong 1-2 ngày làm việc"
        )
        Spacer(Modifier.height(10.dp))
        StepRow(
            number = 3,
            title = "Nhận huy hiệu xác minh",
            subtitle = "Hồ sơ của bạn sẽ có dấu xác minh hiển thị cho khách hàng"
        )
    }
}

@Composable
private fun StepRow(number: Int, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

// ─── Documents section ───────────────────────────────────────────────────────

@Composable
private fun DocumentsSection(
    frontUri: Uri?,
    backUri: Uri?,
    selfieUri: Uri?,
    onFrontSelected: (Uri?) -> Unit,
    onBackSelected: (Uri?) -> Unit,
    onSelfieSelected: (Uri?) -> Unit
) {
    SectionCard(
        icon = Icons.Outlined.CreditCard,
        title = "Tải lên giấy tờ",
        hint = "Đảm bảo ảnh rõ nét, không bị cắt góc, đủ ánh sáng"
    ) {
        DocumentSlot(
            label = "Mặt trước CCCD/CMND",
            description = "Hiển thị rõ ảnh và thông tin",
            uri = frontUri,
            onPicked = onFrontSelected
        )
        Spacer(Modifier.height(12.dp))
        DocumentSlot(
            label = "Mặt sau CCCD/CMND",
            description = "Đảm bảo nhìn rõ mã QR và thông tin",
            uri = backUri,
            onPicked = onBackSelected
        )
        Spacer(Modifier.height(12.dp))
        DocumentSlot(
            label = "Ảnh selfie cầm CCCD/CMND",
            description = "Khuôn mặt và mặt trước giấy tờ phải nhìn rõ",
            icon = Icons.Outlined.CameraAlt,
            uri = selfieUri,
            onPicked = onSelfieSelected
        )
    }
}

@Composable
private fun DocumentSlot(
    label: String,
    description: String,
    uri: Uri?,
    onPicked: (Uri?) -> Unit,
    icon: ImageVector = Icons.Outlined.AddPhotoAlternate
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { result -> onPicked(result) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { launcher.launch("image/*") }
            .border(
                width = 1.dp,
                color = if (uri != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            ),
        color = if (uri != null)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preview thumbnail or empty placeholder.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (uri != null) "Nhấn để chọn ảnh khác" else "Nhấn để tải lên",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (uri != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AccentGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Đã tải",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─── Read-only summary (pending state) ───────────────────────────────────────

@Composable
private fun ReadOnlyDocumentsSummary(state: VerifyIdentityUiState) {
    SectionCard(
        icon = Icons.Outlined.CreditCard,
        title = "Tài liệu đã gửi"
    ) {
        val items = listOfNotNull(
            "Mặt trước CCCD/CMND".takeIf { state.frontUri != null }?.let { it to state.frontUri },
            "Mặt sau CCCD/CMND".takeIf { state.backUri != null }?.let { it to state.backUri },
            "Ảnh selfie cầm giấy tờ".takeIf { state.selfieUri != null }?.let { it to state.selfieUri }
        )
        if (items.isEmpty()) {
            Text(
                text = "Tài liệu đã được lưu trữ an toàn và đang chờ đội ngũ xét duyệt.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            items.forEachIndexed { index, (label, uri) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (uri != null) {
                            AsyncImage(
                                model = uri,
                                contentDescription = label,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Đã tải lên",
                            fontSize = 11.sp,
                            color = AccentGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (index < items.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ─── Personal info ───────────────────────────────────────────────────────────

@Composable
private fun PersonalInfoSection(
    fullName: String,
    idNumber: String,
    onFullNameChange: (String) -> Unit,
    onIdNumberChange: (String) -> Unit
) {
    SectionCard(
        icon = Icons.Outlined.AccountCircle,
        title = "Thông tin cá nhân",
        hint = "Phải khớp với thông tin trên giấy tờ tải lên"
    ) {
        OutlinedTextField(
            value = fullName,
            onValueChange = onFullNameChange,
            label = { Text("Họ và tên") },
            placeholder = { Text("Nguyễn Văn A") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = {
                Icon(
                    Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = idNumber,
            onValueChange = onIdNumberChange,
            label = { Text("Số CCCD/CMND") },
            placeholder = { Text("9-12 chữ số") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = {
                Icon(
                    Icons.Outlined.Badge,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}

// ─── Security note ───────────────────────────────────────────────────────────

@Composable
private fun SecurityNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Thông tin của bạn được mã hoá và chỉ dùng cho mục đích xác minh.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 14.sp
        )
    }
}

// ─── Section card ────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    hint: String? = null,
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
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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

// ─── Submit bar ──────────────────────────────────────────────────────────────

@Composable
private fun SubmitBar(
    enabled: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Button(
                onClick = onSubmit,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (enabled) "Gửi yêu cầu xác minh"
                        else "Hoàn tất các bước trên",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
