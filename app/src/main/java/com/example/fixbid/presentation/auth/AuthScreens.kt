package com.example.fixbid.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.ui.theme.FixBidTheme

// ─── WelcomeScreen ─────────────────────────────────────────────────────────────

@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignInWithGoogle: () -> Unit = {},
    isGoogleLoading: Boolean = false
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Chào mừng",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            AuthLogo(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Sửa nhanh, giá minh bạch",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Đặt thợ sửa chữa chuyên nghiệp chỉ với vài bước. " +
                    "Tạo tài khoản hoặc đăng nhập để tiếp tục.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Đăng nhập",
                onClick = onSignIn
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCreateAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Tạo tài khoản mới",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            AuthDividerRow(label = "hoặc")

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onSignInWithGoogle,
                enabled = !isGoogleLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
            ) {
                if (isGoogleLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Đang kết nối Google…", color = MaterialTheme.colorScheme.onSurface)
                } else {
                    AuthSocialIcon(label = "G", contentColor = Color(0xFF4285F4))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Tiếp tục với Google", color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─── LoginScreen ───────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(
    state: LoginFormState,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onGoToRegister: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            AuthLogo(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Đăng nhập",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Nhập email hoặc số điện thoại và mật khẩu của bạn",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            AuthTextField(
                value = state.identifier,
                onValueChange = onIdentifierChange,
                label = "Email hoặc số điện thoại",
                placeholder = "vd: ban@gmail.com hoặc +84…",
                leadingIcon = Icons.Outlined.AlternateEmail,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                enabled = !state.isSubmitting
            )

            Spacer(modifier = Modifier.height(14.dp))

            AuthTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = "Mật khẩu",
                placeholder = "Nhập mật khẩu",
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                imeAction = ImeAction.Done,
                enabled = !state.isSubmitting
            )

            AuthErrorText(message = state.errorMessage)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Quên mật khẩu?",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { onForgotPassword() }
            )

            Spacer(modifier = Modifier.height(28.dp))

            val canSubmit = !state.isSubmitting &&
                state.identifier.isNotBlank() &&
                state.password.isNotBlank()

            PrimaryButton(
                text = "Đăng nhập",
                onClick = onSubmit,
                enabled = canSubmit,
                isLoading = state.isSubmitting
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Chưa có tài khoản? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    text = "Đăng ký ngay",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onGoToRegister() }
                )
            }
        }
    }
}

// ─── RegisterScreen ────────────────────────────────────────────────────────────

@Composable
fun RegisterScreen(
    state: RegisterFormState,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onAcceptTermsChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onGoToLogin: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            AuthLogo(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Tạo tài khoản",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Điền thông tin bên dưới để bắt đầu sử dụng FixBid",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            RolePicker(
                selected = state.role,
                onSelect = onRoleChange,
                enabled = !state.isSubmitting
            )

            Spacer(modifier = Modifier.height(18.dp))

            AuthTextField(
                value = state.fullName,
                onValueChange = onFullNameChange,
                label = "Họ và tên",
                placeholder = "Nguyễn Văn A",
                leadingIcon = Icons.Outlined.Badge,
                enabled = !state.isSubmitting
            )

            Spacer(modifier = Modifier.height(14.dp))

            AuthTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = "Email",
                placeholder = "ban@gmail.com",
                leadingIcon = Icons.Outlined.Email,
                keyboardType = KeyboardType.Email,
                enabled = !state.isSubmitting
            )

            Spacer(modifier = Modifier.height(14.dp))

            AuthTextField(
                value = state.phoneNumber,
                onValueChange = onPhoneChange,
                label = "Số điện thoại",
                placeholder = "+84…",
                leadingIcon = Icons.Outlined.Phone,
                keyboardType = KeyboardType.Phone,
                enabled = !state.isSubmitting
            )

            Spacer(modifier = Modifier.height(14.dp))

            AuthTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = "Mật khẩu",
                placeholder = "Tối thiểu 8 ký tự",
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                enabled = !state.isSubmitting
            )

            if (state.password.isNotEmpty()) {
                PasswordRequirementsList(password = state.password)
            }

            Spacer(modifier = Modifier.height(14.dp))

            AuthTextField(
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = "Xác nhận mật khẩu",
                placeholder = "Nhập lại mật khẩu",
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                imeAction = ImeAction.Done,
                enabled = !state.isSubmitting
            )

            if (state.confirmPassword.isNotEmpty()) {
                ConfirmPasswordRequirement(isMatched = state.password == state.confirmPassword)
            }

            val displayError = if (state.errorMessage?.contains("weak_password", ignoreCase = true) == true) {
                "Mật khẩu chưa đủ mạnh. Vui lòng kiểm tra các yêu cầu bên trên."
            } else {
                state.errorMessage
            }
            AuthErrorText(message = displayError)

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = state.acceptedTerms,
                    onCheckedChange = onAcceptTermsChange,
                    enabled = !state.isSubmitting,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                val termsText = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("Tôi đồng ý với ") }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                        append("Điều khoản")
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" và ") }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                        append("Chính sách bảo mật")
                    }
                }
                Text(text = termsText, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            PrimaryButton(
                text = "Tạo tài khoản",
                onClick = onSubmit,
                enabled = !state.isSubmitting && state.acceptedTerms,
                isLoading = state.isSubmitting
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Đã có tài khoản? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    text = "Đăng nhập",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onGoToLogin() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RolePicker(
    selected: UserRole,
    onSelect: (UserRole) -> Unit,
    enabled: Boolean
) {
    Column {
        Text(
            text = "Bạn là?",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RoleOption(
                modifier = Modifier.weight(1f),
                title = UserRole.CUSTOMER.displayName,
                description = "Tìm & đặt thợ",
                selected = selected == UserRole.CUSTOMER,
                enabled = enabled,
                onClick = { onSelect(UserRole.CUSTOMER) }
            )
            RoleOption(
                modifier = Modifier.weight(1f),
                title = UserRole.WORKER.displayName,
                description = "Nhận công việc",
                selected = selected == UserRole.WORKER,
                enabled = enabled,
                onClick = { onSelect(UserRole.WORKER) }
            )
        }
    }
}

@Composable
private fun RoleOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .clip(shape = RoundedCornerShape(14.dp))
            .background(background, RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

// ─── OtpVerificationScreen ─────────────────────────────────────────────────────

@Composable
fun OtpVerificationScreen(
    state: OtpFormState,
    onOtpChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onEditContact: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            AuthLogo(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Nhập mã xác thực",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            val destinationLabel = if (state.method == AuthMethod.Email) "email" else "số điện thoại"
            Text(
                text = "Chúng tôi đã gửi mã xác thực tới $destinationLabel của bạn",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.sentTo,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chỉnh",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onEditContact() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OtpInputRow(
                value = state.otp,
                onValueChange = onOtpChange,
                modifier = Modifier.fillMaxWidth(),
                length = AuthViewModel.OTP_LENGTH
            )

            AuthErrorText(message = state.errorMessage)

            Spacer(modifier = Modifier.height(16.dp))

            AuthResendRow(
                resendSeconds = state.resendSeconds,
                isSending = state.isResending,
                onResend = onResend
            )

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Xác nhận",
                onClick = onVerify,
                enabled = !state.isVerifying && state.otp.length == AuthViewModel.OTP_LENGTH,
                isLoading = state.isVerifying
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─── OAuthRoleSelectionScreen ──────────────────────────────────────────────────

/**
 * Shown right after a successful OAuth (Google) sign-in if the user does not
 * yet have a `profiles` row. Lets them pick whether they're a customer or a
 * worker before we create the row and let them into the app.
 */
@Composable
fun OAuthRoleSelectionScreen(
    onSelect: (UserRole) -> Unit,
    onSignOut: () -> Unit
) {
    val selected = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(UserRole.CUSTOMER)
    }
    val isSubmitting = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            AuthLogo(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Bạn muốn dùng FixBid như?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Chọn vai trò để hoàn tất tài khoản. Bạn có thể đổi sau trong phần Hồ sơ.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OAuthRoleCard(
                    modifier = Modifier.weight(1f),
                    title = UserRole.CUSTOMER.displayName,
                    description = "Tìm và đặt thợ sửa chữa tin cậy",
                    selected = selected.value == UserRole.CUSTOMER,
                    enabled = !isSubmitting.value,
                    onClick = { selected.value = UserRole.CUSTOMER }
                )
                OAuthRoleCard(
                    modifier = Modifier.weight(1f),
                    title = UserRole.WORKER.displayName,
                    description = "Nhận đơn, báo giá và làm việc",
                    selected = selected.value == UserRole.WORKER,
                    enabled = !isSubmitting.value,
                    onClick = { selected.value = UserRole.WORKER }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Tiếp tục",
                onClick = {
                    isSubmitting.value = true
                    onSelect(selected.value)
                },
                enabled = !isSubmitting.value,
                isLoading = isSubmitting.value
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Đăng xuất và quay lại",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isSubmitting.value) { onSignOut() }
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun OAuthRoleCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background, RoundedCornerShape(16.dp))
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

// ─── ForgotPasswordScreen ──────────────────────────────────────────────────────
@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordFormState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            AuthLogo(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Quên mật khẩu",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Nhập email của bạn để nhận liên kết đặt lại mật khẩu",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            AuthTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = "Email",
                placeholder = "ban@gmail.com",
                leadingIcon = Icons.Outlined.Email,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                enabled = !state.isSubmitting
            )

            AuthErrorText(message = state.errorMessage)
            AuthSuccessText(message = state.successMessage)

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Gửi liên kết",
                onClick = onSubmit,
                enabled = !state.isSubmitting && state.email.isNotBlank(),
                isLoading = state.isSubmitting
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun WelcomePreview() {
    FixBidTheme {
        WelcomeScreen(onSignIn = {}, onCreateAccount = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    FixBidTheme {
        LoginScreen(
            state = LoginFormState(identifier = "ban@gmail.com"),
            onIdentifierChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onForgotPassword = {},
            onGoToRegister = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterPreview() {
    FixBidTheme {
        RegisterScreen(
            state = RegisterFormState(),
            onFullNameChange = {},
            onEmailChange = {},
            onPhoneChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onRoleChange = {},
            onAcceptTermsChange = {},
            onSubmit = {},
            onGoToLogin = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OtpPreview() {
    FixBidTheme {
        OtpVerificationScreen(
            state = OtpFormState(sentTo = "ban@gmail.com"),
            onOtpChange = {},
            onVerify = {},
            onResend = {},
            onEditContact = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordPreview() {
    FixBidTheme {
        ForgotPasswordScreen(
            state = ForgotPasswordFormState(),
            onEmailChange = {},
            onSubmit = {},
            onBack = {}
        )
    }
}
