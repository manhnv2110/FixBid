package com.example.fixbid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fixbid.presentation.auth.AuthApp
import com.example.fixbid.presentation.auth.WelcomeScreen
import com.example.fixbid.ui.theme.FixBidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FixBidTheme {
                AuthApp()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    FixBidTheme {
        WelcomeScreen(onSignIn = {}, onCreateAccount = {})
    }
}
