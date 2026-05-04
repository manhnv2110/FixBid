package com.example.fixbid

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fixbid.presentation.customer.home.HomeScreen
import com.example.fixbid.ui.theme.FixBidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = Color.TRANSPARENT // Đảm bảo nền status bar vẫn trong suốt để lộ nền xanh của bạn
            )
        )
        setContent {
            FixBidTheme {
                HomeScreen(
                    onCategoryClick = { /* TODO: navigate */ },
                    onNotificationClick = { /* TODO: navigate */ },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    FixBidTheme {
        HomeScreen(
            onCategoryClick = { },
            onNotificationClick = { },
        )
    }
}