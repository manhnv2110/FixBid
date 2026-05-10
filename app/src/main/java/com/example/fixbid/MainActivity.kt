package com.example.fixbid

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fixbid.presentation.customer.booking.BookingScreen
import com.example.fixbid.presentation.customer.booking.BookingSuccessScreen
import com.example.fixbid.presentation.customer.bidding.BiddingWorkersScreen
import com.example.fixbid.presentation.customer.home.HomeScreen
import com.example.fixbid.ui.theme.FixBidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = Color.TRANSPARENT 
            )
        )
        setContent {
            FixBidTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onCategoryClick = { category ->
                                navController.navigate("booking/${category.name}")
                            },
                            onNotificationClick = { /* TODO */ },
                            onBookingClick = { bookingId ->
                                navController.navigate("bidding_workers/$bookingId")
                            }
                        )
                    }
                    composable(
                        route = "booking/{categoryName}",
                        arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val categoryName = backStackEntry.arguments?.getString("categoryName")
                        BookingScreen(
                            initialCategoryName = categoryName,
                            onBackClick = { navController.popBackStack() },
                            onSubmitSuccess = { navController.navigate("booking_success") }
                        )
                    }
                    composable("booking_success") {
                        BookingSuccessScreen(
                            onExploreOtherServicesClick = {
                                navController.popBackStack("home", inclusive = false)
                            },
                            onHomeClick = {
                                navController.popBackStack("home", inclusive = false)
                            }
                        )
                    }
                    composable(
                        route = "bidding_workers/{bookingId}",
                        arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val bookingId = backStackEntry.arguments?.getString("bookingId")
                        BiddingWorkersScreen(
                            bookingId = bookingId,
                            onBackClick = { navController.popBackStack() },
                            onWorkerClick = { workerId ->
                                /* TODO: Navigate to worker profile or confirm booking */
                            }
                        )
                    }
                }
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
            onBookingClick = { }
        )
    }
}