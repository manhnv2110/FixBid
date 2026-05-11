package com.example.fixbid.presentation.auth

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fixbid.presentation.customer.home.HomeScreen

@Composable
fun AuthApp(
    viewModel: AuthViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.NavigateToOtp -> {
                    navController.navigate(AuthRoutes.Otp) {
                        launchSingleTop = true
                    }
                }
                AuthEvent.NavigateToHome -> {
                    navController.navigate(AuthRoutes.Home) {
                        popUpTo(AuthRoutes.Welcome) { inclusive = true }
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

    val startDestination = if (uiState.isAuthenticated) AuthRoutes.Home else AuthRoutes.Welcome

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(AuthRoutes.Welcome) {
            WelcomeScreen(
                onSignIn = { navController.navigate(AuthRoutes.Login) },
                onCreateAccount = { navController.navigate(AuthRoutes.Register) }
            )
        }

        composable(AuthRoutes.Login) {
            LoginScreen(
                state = uiState.login,
                onIdentifierChange = viewModel::onLoginIdentifierChange,
                onPasswordChange = viewModel::onLoginPasswordChange,
                onSubmit = viewModel::submitLogin,
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
                onFullNameChange = viewModel::onRegisterFullNameChange,
                onEmailChange = viewModel::onRegisterEmailChange,
                onPhoneChange = viewModel::onRegisterPhoneChange,
                onPasswordChange = viewModel::onRegisterPasswordChange,
                onConfirmPasswordChange = viewModel::onRegisterConfirmPasswordChange,
                onRoleChange = viewModel::onRegisterRoleChange,
                onAcceptTermsChange = viewModel::onRegisterAcceptTermsChange,
                onSubmit = viewModel::submitRegister,
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
                onOtpChange = viewModel::onOtpChange,
                onVerify = viewModel::verifyOtp,
                onResend = viewModel::resendOtp,
                onEditContact = {
                    viewModel.clearOtpState()
                    navController.popBackStack(AuthRoutes.Register, inclusive = false)
                },
                onBack = {
                    viewModel.clearOtpState()
                    navController.popBackStack()
                }
            )
        }

        composable(AuthRoutes.ForgotPassword) {
            ForgotPasswordScreen(
                state = uiState.forgotPassword,
                onEmailChange = viewModel::onForgotEmailChange,
                onSubmit = viewModel::submitForgotPassword,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AuthRoutes.Home) {
            HomeScreen()
        }
    }
}
