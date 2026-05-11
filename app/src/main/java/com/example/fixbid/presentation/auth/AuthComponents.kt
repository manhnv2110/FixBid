package com.example.fixbid.presentation.auth

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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.fixbid.ui.theme.AccentGreen
import com.example.fixbid.ui.theme.AuthBorder
import com.example.fixbid.ui.theme.AuthMuted
import com.example.fixbid.ui.theme.AuthSurface
import com.example.fixbid.ui.theme.PrimaryBlue
import com.example.fixbid.ui.theme.TextPrimary
import com.example.fixbid.ui.theme.TextSecondary
import com.example.fixbid.ui.theme.White

/** Brand word-mark shown at the top of every auth screen. */
@Composable
fun AuthLogo(modifier: Modifier = Modifier) {
    val logo = buildAnnotatedString {
        append("FI")
        withStyle(SpanStyle(color = PrimaryBlue)) { append("X") }
        append("BID")
    }
    Text(
        text = logo,
        modifier = modifier,
        style = TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        ),
        color = TextPrimary,
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
            containerColor = PrimaryBlue,
            disabledContainerColor = Color(0xFFE5E7EB),
            disabledContentColor = AuthMuted
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
                color = White
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = if (enabled) White else AuthMuted,
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
            containerColor = White,
            contentColor = PrimaryBlue,
            disabledContainerColor = Color(0xFFF3F4F6),
            disabledContentColor = AuthMuted
        )
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
            color = TextSecondary,
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
            placeholder = placeholder?.let { { Text(it, color = AuthMuted) } },
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, tint = AuthMuted) }
            },
            trailingIcon = when {
                isPassword -> {
                    {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff
                                              else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                tint = AuthMuted
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
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = AuthBorder,
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = PrimaryBlue
            )
        )
    }
}

/** Small helper so the password icon import is visible for callers. */
@Composable
fun PasswordLeadingIcon() = Icon(
    imageVector = Icons.Outlined.Lock,
    contentDescription = null,
    tint = AuthMuted
)

@Composable
fun AuthErrorText(message: String?, modifier: Modifier = Modifier) {
    if (!message.isNullOrBlank()) {
        Text(
            text = message,
            color = Color(0xFFB42318),
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
            color = AccentGreen,
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
        HorizontalDivider(modifier = Modifier.weight(1f), color = AuthBorder)
        Text(
            text = label,
            color = AuthMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = AuthBorder)
    }
}

@Composable
fun AuthSocialIcon(
    label: String,
    background: Color = AuthSurface,
    contentColor: Color = TextPrimary
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(background, CircleShape)
            .border(1.dp, AuthBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
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
        cursorBrush = SolidColor(PrimaryBlue),
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
                            isFilled -> AccentGreen
                            isFocused -> PrimaryBlue
                            else -> AuthBorder
                        }
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(54.dp)
                                .background(AuthSurface, RoundedCornerShape(12.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
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
        withStyle(SpanStyle(color = TextSecondary)) { append("Không nhận được mã? ") }
        withStyle(
            SpanStyle(
                color = if (isDisabled) AuthMuted else PrimaryBlue,
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
