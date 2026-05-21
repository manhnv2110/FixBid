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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand word-mark shown at the top of every auth screen. */
@Composable
fun AuthLogo(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val logo = remember(primaryColor) {
        buildAnnotatedString {
            append("FI")
            withStyle(SpanStyle(color = primaryColor)) { append("X") }
            append("BID")
        }
    }
    Text(
        text = logo,
        modifier = modifier,
        style = TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Labelled text field styled to match the app's auth screens. */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val visualTransformation = when {
        isPassword && !passwordVisible -> PasswordVisualTransformation()
        else -> VisualTransformation.None
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = singleLine,
            enabled = enabled,
            placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) } },
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
            },
            trailingIcon = when {
                isPassword -> {
                    {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff
                                              else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                trailingIcon != null -> trailingIcon
                else -> null
            },
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/** Small helper so the password icon import is visible for callers. */
@Composable
fun PasswordLeadingIcon() = Icon(
    imageVector = Icons.Outlined.Lock,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
)

@Composable
fun AuthErrorText(message: String?, modifier: Modifier = Modifier) {
    if (!message.isNullOrBlank()) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun AuthSuccessText(message: String?, modifier: Modifier = Modifier) {
    if (!message.isNullOrBlank()) {
        Text(
            text = message,
            color = Color(0xFF4CAF50), // standard success green
            fontSize = 12.sp,
            modifier = modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun AuthDividerRow(label: String = "hoặc") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun AuthSocialIcon(
    label: String,
    background: Color = Color.Transparent,
    contentColor: Color? = null
) {
    val realContentColor = contentColor ?: MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(background, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = realContentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OtpInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6
) {
    val textStyle = TextStyle(
        color = Color.Transparent,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold
    )

    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            val filtered = newValue.filter { it.isDigit() }.take(length)
            onValueChange(filtered)
        },
        modifier = modifier,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    repeat(length) { index ->
                        val char = value.getOrNull(index)?.toString().orEmpty()
                        val isFilled = index < value.length
                        val isFocused = value.length == index ||
                            (value.length == length && index == length - 1)
                        val borderColor = when {
                            isFilled -> Color(0xFF4CAF50)
                            isFocused -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(54.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0f)
                ) { innerTextField() }
            }
        }
    )
}

@Composable
fun AuthResendRow(
    resendSeconds: Int,
    isSending: Boolean,
    onResend: () -> Unit
) {
    val isDisabled = resendSeconds > 0 || isSending
    val actionText = if (resendSeconds > 0) "Gửi lại sau ${resendSeconds}s" else "Gửi lại mã"
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("Không nhận được mã? ") }
        withStyle(
            SpanStyle(
                color = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        ) { append(actionText) }
    }
    Text(
        text = annotated,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDisabled) { onResend() }
    )
}

@Composable
fun PasswordRequirementsList(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return

    val hasMinLength = password.length >= 8
    val hasLowercase = password.any { it.isLowerCase() }
    val hasUppercase = password.any { it.isUpperCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSpecialChar = password.any { !it.isLetterOrDigit() }

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        PasswordRequirementRow(text = "Ít nhất 8 ký tự", isMet = hasMinLength)
        PasswordRequirementRow(text = "Ít nhất 1 chữ thường (a-z)", isMet = hasLowercase)
        PasswordRequirementRow(text = "Ít nhất 1 chữ hoa (A-Z)", isMet = hasUppercase)
        PasswordRequirementRow(text = "Ít nhất 1 số (0-9)", isMet = hasDigit)
        PasswordRequirementRow(text = "Ít nhất 1 ký tự đặc biệt", isMet = hasSpecialChar)
    }
}

@Composable
fun ConfirmPasswordRequirement(isMatched: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        PasswordRequirementRow(text = "Mật khẩu trùng khớp", isMet = isMatched)
    }
}

@Composable
fun PasswordRequirementRow(text: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isMet) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = if (isMet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
