package com.example.fixbid

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.presentation.auth.*
import com.example.fixbid.presentation.customer.bidding.BiddingWorkersScreen
import com.example.fixbid.presentation.customer.booking.BookingScreen
import com.example.fixbid.presentation.customer.booking.BookingSuccessScreen
import com.example.fixbid.presentation.customer.home.HomeScreen
import com.example.fixbid.presentation.worker.home.WorkerHomeScreen
import com.example.fixbid.ui.theme.FixBidTheme
import com.example.fixbid.ui.theme.PrimaryBlue
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FixBidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FixBidNavHost()
                }
            }
        }
    }
}

@Composable
fun FixBidNavHost() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val uiState by authViewModel.uiState.collectAsState()

    // Show loading while bootstrapping (checking saved session)
    if (uiState.isBootstrapping) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }

    val navController = rememberNavController()
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val needsLightIcons = currentRoute == "home" || currentRoute == "worker_home"
    com.example.fixbid.ui.theme.SetStatusBarColor(darkIcons = !needsLightIcons)

    // Listen to auth events for navigation
    LaunchedEffect(Unit) {
        authViewModel.events.collect { event ->
            when (event) {
                AuthEvent.NavigateToOtp -> {
                    navController.navigate(AuthRoutes.Otp) {
                        launchSingleTop = true
                    }
                }
                AuthEvent.NavigateToHome -> {
                    val destination = if (uiState.userRole == UserRole.WORKER) "worker_home" else "home"
                    navController.navigate(destination) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                AuthEvent.NavigateBackToLogin -> {
                    navController.popBackStack(AuthRoutes.Login, inclusive = false)
                }
                is AuthEvent.Toast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Determine start destination based on auth state
    val startDestination = when {
        !uiState.isAuthenticated -> AuthRoutes.Welcome
        uiState.userRole == UserRole.WORKER -> "worker_home"
        else -> "home"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
        // ─── Auth screens ─────────────────────────────────────────────────
        composable(AuthRoutes.Welcome) {
            WelcomeScreen(
                onSignIn = { navController.navigate(AuthRoutes.Login) },
                onCreateAccount = { navController.navigate(AuthRoutes.Register) }
            )
        }

        composable(AuthRoutes.Login) {
            LoginScreen(
                state = uiState.login,
                onIdentifierChange = authViewModel::onLoginIdentifierChange,
                onPasswordChange = authViewModel::onLoginPasswordChange,
                onSubmit = authViewModel::submitLogin,
                onForgotPassword = { navController.navigate(AuthRoutes.ForgotPassword) },
                onGoToRegister = {
                    navController.navigate(AuthRoutes.Register) {
                        popUpTo(AuthRoutes.Login) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AuthRoutes.Register) {
            RegisterScreen(
                state = uiState.register,
                onFullNameChange = authViewModel::onRegisterFullNameChange,
                onEmailChange = authViewModel::onRegisterEmailChange,
                onPhoneChange = authViewModel::onRegisterPhoneChange,
                onPasswordChange = authViewModel::onRegisterPasswordChange,
                onConfirmPasswordChange = authViewModel::onRegisterConfirmPasswordChange,
                onRoleChange = authViewModel::onRegisterRoleChange,
                onAcceptTermsChange = authViewModel::onRegisterAcceptTermsChange,
                onSubmit = authViewModel::submitRegister,
                onGoToLogin = {
                    navController.navigate(AuthRoutes.Login) {
                        popUpTo(AuthRoutes.Register) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AuthRoutes.Otp) {
            OtpVerificationScreen(
                state = uiState.otp,
                onOtpChange = authViewModel::onOtpChange,
                onVerify = authViewModel::verifyOtp,
                onResend = authViewModel::resendOtp,
                onEditContact = {
                    authViewModel.clearOtpState()
                    navController.popBackStack(AuthRoutes.Register, inclusive = false)
                },
                onBack = {
                    authViewModel.clearOtpState()
                    navController.popBackStack()
                }
            )
        }

        composable(AuthRoutes.ForgotPassword) {
            ForgotPasswordScreen(
                state = uiState.forgotPassword,
                onEmailChange = authViewModel::onForgotEmailChange,
                onSubmit = authViewModel::submitForgotPassword,
                onBack = { navController.popBackStack() }
            )
        }

        // ─── Main app screens ─────────────────────────────────────────────
        composable("home") {
            val showHistory = it.savedStateHandle.get<Boolean>("show_history") == true
            // Consume the flag
            LaunchedEffect(showHistory) {
                if (showHistory) {
                    it.savedStateHandle.remove<Boolean>("show_history")
                }
            }
            HomeScreen(
                onCategoryClick = { category ->
                    navController.navigate("booking/${category.name}")
                },
                onNotificationClick = { /* TODO: notification list screen */ },
                onBookingClick = { bookingId ->
                    navController.navigate("bidding_workers/$bookingId")
                },
                onCompletionConfirmClick = { bookingId ->
                    navController.navigate("completion_confirm/$bookingId")
                },
                onSignOut = {
                    navController.navigate(AuthRoutes.Welcome) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                showHistoryTab = showHistory
            )
        }

        // ─── Worker screens ───────────────────────────────────────────────
        composable("worker_home") {
            WorkerHomeScreen(
                onNotificationClick = { /* TODO: notification list screen */ },
                onJobClick = { bookingId ->
                    navController.navigate("worker_job_detail/$bookingId")
                },
                onJobRequestClick = { bookingId ->
                    navController.navigate("worker_job_detail/$bookingId")
                },
                onSignOut = {
                    navController.navigate(AuthRoutes.Welcome) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "worker_job_detail/{bookingId}",
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) {
            com.example.fixbid.presentation.worker.jobdetail.JobDetailScreen(
                onBackClick = { navController.popBackStack() }
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
                    // Navigate back to home and signal to show history tab
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("show_history", true)
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }

        composable(
            route = "bidding_workers/{bookingId}",
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            BiddingWorkersScreen(
                bookingId = bookingId,
                onBackClick = { navController.popBackStack() },
                onWorkerClick = { workerId ->
                    /* TODO: Navigate to worker profile */
                }
            )
        }

        composable(
            route = "completion_confirm/{bookingId}",
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) {
            com.example.fixbid.presentation.customer.completion.CompletionConfirmScreen(
                onBackClick = { navController.popBackStack() },
                onCompleted = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
    }
}