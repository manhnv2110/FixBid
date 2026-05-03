package com.example.fixbid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fixbid.presentation.customer.home.HomeScreen
import com.example.fixbid.ui.theme.FixBidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FixBidTheme {
                HomeScreen(
                    onCategoryClick = TODO(),
                    onNotificationClick = TODO(),
                    viewModel = TODO()
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
            onCategoryClick = TODO(),
            onNotificationClick = TODO(),
            viewModel = TODO()
        )
    }
}