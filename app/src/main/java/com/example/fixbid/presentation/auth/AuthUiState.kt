package com.example.fixbid.presentation.auth

import com.example.fixbid.domain.model.UserRole

/**
 * Signals which auth identifier is being used in flows that accept both
 * email and phone (e.g. sign-in and OTP verification).
 */
enum class AuthMethod {
    Phone,
    Email
}

/**
 * Form state for the sign-in screen.
 */
data class LoginFormState(
    val identifier: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Form state for the multi-field sign-up screen.
 */
data class RegisterFormState(
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val role: UserRole = UserRole.CUSTOMER,
    val acceptedTerms: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Form state for the "forgot password" flow (email link).
 */
data class ForgotPasswordFormState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * State for the OTP verification screen shown after registration.
 */
data class OtpFormState(
    val otp: String = "",
    val isVerifying: Boolean = false,
    val isResending: Boolean = false,
    val resendSeconds: Int = 0,
    val errorMessage: String? = null,
    /** Where the code was sent (email address or phone). */
    val sentTo: String = "",
    val method: AuthMethod = AuthMethod.Email
)

/**
 * Aggregates all of the auth-flow UI state so a single ViewModel can drive
 * every auth screen (welcome, login, register, OTP, forgot password).
 */
data class AuthUiState(
    val isAuthenticated: Boolean = false,
    val isBootstrapping: Boolean = true,
    val login: LoginFormState = LoginFormState(),
    val register: RegisterFormState = RegisterFormState(),
    val forgotPassword: ForgotPasswordFormState = ForgotPasswordFormState(),
    val otp: OtpFormState = OtpFormState(),
    /** Persisted across Register → OTP → Home so we can create the profile row. */
    val pendingRegistration: PendingRegistration? = null
)

/**
 * Snapshot of the data the user entered on the register screen. We keep it
 * around until OTP verification succeeds so the row we insert into
 * `public.profiles` matches what they filled in.
 */
data class PendingRegistration(
    val email: String,
    val fullName: String,
    val phoneNumber: String?,
    val role: UserRole
)
