package com.example.fixbid.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    background = BackgroundGray,
    surface = CardWhite,
    onPrimary = White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun FixBidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}