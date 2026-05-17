package com.example.fixbid.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    background = BackgroundGray,
    surface = CardWhite,
    onPrimary = White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)



@Composable
fun SetStatusBarColor(darkIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(darkIcons) {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            val original = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = darkIcons
            onDispose {
                controller.isAppearanceLightStatusBars = original
            }
        }
    }
}

@Composable
fun FixBidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}