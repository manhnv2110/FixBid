package com.example.fixbid.presentation.support

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.BuildConfig
import com.example.fixbid.core.components.AppHeader

/**
 * "Về FixBid" screen — surfaced from Profile → Về FixBid.
 *
 * Three sections:
 *  - **Hero**: animated brand badge + app name + version (read live from
 *    BuildConfig so QA always sees the actual build they're holding).
 *  - **Mô tả**: 2-paragraph intro of what FixBid does, written for end
 *    users (not technical jargon).
 *  - **Liên kết**: privacy policy, terms, contact email, website. Each row
 *    fires the appropriate Intent (browser / mailto / dialer) and falls
 *    back to a toast if the device has no app to handle it.
 *  - **OSS**: list of major libraries we depend on, so the screen can
 *    double as a "Open-source licenses" surface required by some review
 *    processes.
 *
 * No ViewModel — the data is static (BuildConfig + hardcoded strings).
 */
@Composable
fun AboutScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AppHeader(title = "Về FixBid", onBackClick = onBackClick)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Hero ────────────────────────────────────────────────────────
            HeroBlock()

            // ── Description ─────────────────────────────────────────────────
            DescriptionCard()

            // ── Quick links ─────────────────────────────────────────────────
            LinksCard(
                onPrivacy = {
                    openUrl(context, "https://fixbid.vn/privacy")
                },
                onTerms = {
                    openUrl(context, "https://fixbid.vn/terms")
                },
                onWebsite = {
                    openUrl(context, "https://fixbid.vn")
                },
                onContact = {
                    sendEmail(context, "support@fixbid.vn", "Hỗ trợ FixBid")
                }
            )

            // ── OSS attributions ────────────────────────────────────────────
            OpenSourceCard()

            // ── Footer ──────────────────────────────────────────────────────
            Text(
                text = "© 2026 FixBid Team",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeroBlock() {
    // Subtle scale-pulse on the badge so the screen feels alive without
    // committing to a heavy splash animation.
    val transition = rememberInfiniteTransition(label = "about-pulse")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "about-pulse-scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "FixBid",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Nền tảng kết nối thợ dịch vụ thông minh",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Phiên bản ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DescriptionCard() {
    SectionCard(title = "Về ứng dụng") {
        Text(
            text = "FixBid kết nối khách hàng với các thợ dịch vụ uy tín — từ điện, " +
                "nước, điều hoà, vệ sinh nhà cửa đến các dịch vụ chuyên môn khác. " +
                "Bạn có thể chọn thẳng một thợ tin tưởng hoặc đăng yêu cầu để nhận " +
                "báo giá cạnh tranh từ nhiều thợ.",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Mọi giao dịch được bảo vệ bằng cơ chế giữ tiền (escrow) — tiền " +
                "chỉ chuyển cho thợ khi bạn xác nhận hài lòng với kết quả công việc. " +
                "Trợ lý AI tích hợp giúp bạn tìm thợ, đánh giá báo giá và soạn lời " +
                "nhắn nhanh chóng.",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LinksCard(
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onWebsite: () -> Unit,
    onContact: () -> Unit
) {
    SectionCard(title = "Liên kết") {
        LinkRow(
            icon = Icons.Outlined.PrivacyTip,
            label = "Chính sách bảo mật",
            onClick = onPrivacy
        )
        DividerInset()
        LinkRow(
            icon = Icons.Outlined.Gavel,
            label = "Điều khoản sử dụng",
            onClick = onTerms
        )
        DividerInset()
        LinkRow(
            icon = Icons.Outlined.Public,
            label = "Trang web",
            subtitle = "fixbid.vn",
            onClick = onWebsite
        )
        DividerInset()
        LinkRow(
            icon = Icons.Outlined.Email,
            label = "Liên hệ hỗ trợ",
            subtitle = "support@fixbid.vn",
            onClick = onContact
        )
    }
}

@Composable
private fun OpenSourceCard() {
    SectionCard(title = "Công nghệ sử dụng") {
        Text(
            text = "FixBid được xây dựng trên nền tảng Android với các thư viện mã " +
                "nguồn mở. Cảm ơn các dự án dưới đây đã giúp chúng tôi tạo nên ứng dụng:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        listOf(
            "Jetpack Compose & Material 3 — UI framework",
            "Hilt — Dependency Injection",
            "Supabase Kotlin SDK — Backend & Realtime",
            "Ktor — HTTP client",
            "Coil — Image loading",
            "MapLibre Native — Maps rendering",
            "OSRM — Routing engine",
            "Groq — AI assistant",
            "Firebase Cloud Messaging — Push notifications",
            "VNPay — Payment gateway"
        ).forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DividerInset() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 32.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(
                context,
                "Không tìm thấy trình duyệt để mở liên kết",
                Toast.LENGTH_SHORT
            ).show()
        }
}

private fun sendEmail(context: android.content.Context, address: String, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$address?subject=${Uri.encode(subject)}")
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(
                context,
                "Không có ứng dụng email cài đặt",
                Toast.LENGTH_SHORT
            ).show()
        }
}
