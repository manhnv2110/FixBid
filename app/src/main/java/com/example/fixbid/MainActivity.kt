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
import com.example.fixbid.presentation.customer.chat.ChatScreen
import com.example.fixbid.presentation.customer.home.HomeScreen
import com.example.fixbid.presentation.worker.home.WorkerHomeScreen
import com.example.fixbid.presentation.notification.NotificationListScreen
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
                    FixBidNavHost(intent = intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun FixBidNavHost(intent: android.content.Intent? = null) {
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

    // Handle VNPay deep link return: fixbid://vnpay-return?vnp_...
    LaunchedEffect(intent?.data) {
        val uri = intent?.data ?: return@LaunchedEffect
        if (uri.scheme == "fixbid" && uri.host == "vnpay-return") {
            // Navigate to vnpay_return route with the full URI
            val encodedUri = java.net.URLEncoder.encode(uri.toString(), "UTF-8")
            navController.navigate("vnpay_return/$encodedUri") {
                launchSingleTop = true
            }
        }
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
                onNotificationClick = { navController.navigate("notification_list") },
                onBookingClick = { bookingId ->
                    navController.navigate("customer_booking_detail/$bookingId")
                },
                onBiddingWorkersClick = { bookingId ->
                    navController.navigate("bidding_workers/$bookingId")
                },
                onCompletionConfirmClick = { bookingId ->
                    navController.navigate("completion_confirm/$bookingId")
                },
                onPaymentClick = { bookingId ->
                    navController.navigate("payment/$bookingId")
                },
                onSignOut = {
                    navController.navigate(AuthRoutes.Welcome) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                showHistoryTab = showHistory,
                onChatConversationClick = { conversationId, workerId, workerName ->
                    val encodedName = java.net.URLEncoder.encode(workerName, "UTF-8")
                    navController.navigate("chat/$conversationId/$workerId/$encodedName")
                }
            )
        }

        // ─── Worker screens ───────────────────────────────────────────────
        composable("worker_home") {
            val showWork = it.savedStateHandle.get<Boolean>("show_work") == true
            LaunchedEffect(showWork) {
                if (showWork) it.savedStateHandle.remove<Boolean>("show_work")
            }
            WorkerHomeScreen(
                onNotificationClick = { navController.navigate("notification_list") },
                onJobClick = { bookingId ->
                    navController.navigate("worker_job_detail/$bookingId")
                },
                onJobRequestClick = { bookingId ->
                    navController.navigate("worker_job_detail/$bookingId")
                },
                onBrowseAllRequestsClick = {
                    navController.navigate("worker_requests")
                },
                onSignOut = {
                    navController.navigate(AuthRoutes.Welcome) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                showWorkTab = showWork
            )
        }

        composable("worker_requests") {
            com.example.fixbid.presentation.worker.jobs.JobRequestsScreen(
                onBackClick = { navController.popBackStack() },
                onJobClick = { bookingId ->
                    navController.navigate("worker_job_detail/$bookingId")
                }
            )
        }

        composable(
            route = "worker_job_detail/{bookingId}",
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) {
            com.example.fixbid.presentation.worker.jobdetail.JobDetailScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToCustomer = { bookingId ->
                    navController.navigate("worker_navigation/$bookingId")
                }
            )
        }

        composable(
            route = "worker_navigation/{bookingId}",
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) {
            com.example.fixbid.presentation.worker.navigation.WorkerNavigationScreen(
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
                onSubmitSuccess = { bookingId -> navController.navigate("booking_success/$bookingId") }
            )
        }

        composable(
            route = "booking_success/{bookingId}",
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            BookingSuccessScreen(
                bookingId = bookingId,
                onExploreOtherServicesClick = {
                    navController.popBackStack("home", inclusive = false)
                },
                onViewDetailClick = { bId ->
                    navController.navigate("customer_booking_detail/$bId") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = "customer_booking_detail/{bookingId}",
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) {
            com.example.fixbid.presentation.customer.booking.CustomerBookingDetailScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToBids = { bId ->
                    navController.navigate("bidding_workers/$bId")
                },
                onNavigateToPayment = { bId ->
                    navController.navigate("payment/$bId")
                },
                onNavigateToCompletionConfirm = { bId ->
                    navController.navigate("completion_confirm/$bId")
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
                },
                onNavigateToPayment = { bId ->
                    navController.navigate("payment/$bId")
                },
                onNavigateToChat = { conversationId, workerId, workerName ->
                    val encodedName = java.net.URLEncoder.encode(workerName, "UTF-8")
                    navController.navigate("chat/$conversationId/$workerId/$encodedName")
                }
            )
        }

        // ─── Payment screen ──────────────────────────────────────────────
        composable(
            route = "payment/{bookingId}",
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) {
            com.example.fixbid.presentation.customer.payment.PaymentScreen(
                onBackClick = { navController.popBackStack() },
                onPaymentSuccess = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }

        // ─── VNPay return deep link handler ──────────────────────────────
        composable(
            route = "vnpay_return/{encodedUri}",
            arguments = listOf(navArgument("encodedUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("encodedUri") ?: ""
            val decodedUri = java.net.URLDecoder.decode(encodedUri, "UTF-8")
            com.example.fixbid.presentation.customer.payment.VNPayReturnScreen(
                returnUri = decodedUri,
                onDone = {
                    navController.popBackStack("home", inclusive = false)
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

        composable("notification_list") {
            NotificationListScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ─── Chat screen ──────────────────────────────────────────────────
        composable(
            route = "chat/{conversationId}/{workerId}/{workerName}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("workerId")       { type = NavType.StringType },
                navArgument("workerName")     { type = NavType.StringType }
            )
        ) {
            ChatScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}