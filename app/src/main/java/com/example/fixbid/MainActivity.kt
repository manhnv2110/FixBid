package com.example.fixbid

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            FixBidTheme {
                HomeScreen(
                    onCategoryClick = { category -> /* TODO: navigate */ },
                    onNotificationClick = { /* TODO: navigate */ },
                    // Bỏ tham số viewModel đi — Hilt tự inject qua hiltViewModel()
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
            onCategoryClick = { category -> /* TODO: navigate */ },
            onNotificationClick = { /* TODO: navigate */ },
            // Bỏ tham số viewModel đi — Hilt tự inject qua hiltViewModel()
        )
    }
}