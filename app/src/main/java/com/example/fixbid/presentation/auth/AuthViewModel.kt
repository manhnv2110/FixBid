package com.example.fixbid.presentation.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.data.repository.ProfileRepository
import com.example.fixbid.domain.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.Phone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One-shot navigation/signal events emitted by the auth flow.
 */
sealed interface AuthEvent {
    data object NavigateToOtp : AuthEvent
    data object NavigateToHome : AuthEvent
    data object NavigateBackToLogin : AuthEvent
    data class Toast(val message: String) : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private var resendJob: Job? = null

    init {
        viewModelScope.launch {
            // Đợi session load xong từ storage (không dùng currentSessionOrNull vì có thể chưa sẵn sàng)
            val status = supabase.auth.sessionStatus.first { it !is io.github.jan.supabase.auth.status.SessionStatus.Initializing }
            val isLoggedIn = status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated
            _uiState.update {
                it.copy(
                    isAuthenticated = isLoggedIn,
                    isBootstrapping = false
                )
            }
            if (isLoggedIn) {
                _events.tryEmit(AuthEvent.NavigateToHome)
            }
        }
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    fun onLoginIdentifierChange(value: String) = _uiState.update {
        it.copy(login = it.login.copy(identifier = value.trim(), errorMessage = null))
    }

    fun onLoginPasswordChange(value: String) = _uiState.update {
        it.copy(login = it.login.copy(password = value, errorMessage = null))
    }

    fun submitLogin() {
        val form = _uiState.value.login
        if (form.isSubmitting) return

        val identifier = form.identifier.trim()
        val password = form.password
        val method = detectAuthMethod(identifier)

        if (method == null) {
            setLoginError("Nhập email hoặc số điện thoại hợp lệ")
            return
        }
        if (method == AuthMethod.Email && !isValidEmail(identifier.lowercase())) {
            setLoginError("Email không hợp lệ")
            return
        }
        if (method == AuthMethod.Phone && !isValidPhone(normalizePhone(identifier))) {
            setLoginError("Số điện thoại phải có mã quốc gia (ví dụ +84…)")
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            setLoginError("Mật khẩu cần ít nhất $MIN_PASSWORD_LENGTH ký tự")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(login = it.login.copy(isSubmitting = true, errorMessage = null)) }
            runCatching {
                when (method) {
                    AuthMethod.Email -> {
                        supabase.auth.signInWith(Email) {
                            this.email = identifier.lowercase()
                            this.password = password
                        }
                    }
                    AuthMethod.Phone -> {
                        val phone = normalizePhone(identifier)
                        runCatching {
                            supabase.auth.signInWith(Phone) {
                                this.phone = phone
                                this.password = password
                            }
                        }.recoverCatching {
                            val email = profileRepository.findEmailByPhone(phone).getOrNull()
                                ?: throw IllegalStateException(
                                    "Không tìm thấy tài khoản với số điện thoại này"
                                )
                            supabase.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                        }.getOrThrow()
                    }
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        login = it.login.copy(isSubmitting = false, password = ""),
                        isAuthenticated = true
                    )
                }
                _events.tryEmit(AuthEvent.NavigateToHome)
            }.onFailure { error ->
                _uiState.update { it.copy(login = it.login.copy(isSubmitting = false)) }
                setLoginError(parseSupabaseError(error))
            }
        }
    }

    // ─── Register ─────────────────────────────────────────────────────────────

    fun onRegisterFullNameChange(value: String) = _uiState.update {
        it.copy(register = it.register.copy(fullName = value, errorMessage = null))
    }

    fun onRegisterEmailChange(value: String) = _uiState.update {
        it.copy(register = it.register.copy(email = value.trim(), errorMessage = null))
    }

    fun onRegisterPhoneChange(value: String) = _uiState.update {
        val filtered = value.filterIndexed { index, c ->
            c.isDigit() || (c == '+' && index == 0) || c == ' ' || c == '-' || c == '(' || c == ')'
        }
        it.copy(register = it.register.copy(phoneNumber = filtered, errorMessage = null))
    }

    fun onRegisterPasswordChange(value: String) = _uiState.update {
        it.copy(register = it.register.copy(password = value, errorMessage = null))
    }

    fun onRegisterConfirmPasswordChange(value: String) = _uiState.update {
        it.copy(register = it.register.copy(confirmPassword = value, errorMessage = null))
    }

    fun onRegisterRoleChange(role: UserRole) = _uiState.update {
        it.copy(register = it.register.copy(role = role, errorMessage = null))
    }

    fun onRegisterAcceptTermsChange(accepted: Boolean) = _uiState.update {
        it.copy(register = it.register.copy(acceptedTerms = accepted, errorMessage = null))
    }

    fun submitRegister() {
        val form = _uiState.value.register
        if (form.isSubmitting) return

        val fullName = form.fullName.trim()
        val email = form.email.trim().lowercase()
        val rawPhone = form.phoneNumber.trim()
        val phone = if (rawPhone.isNotEmpty()) normalizePhone(rawPhone) else null

        when {
            fullName.length < 2 ->
                return setRegisterError("Vui lòng nhập họ tên đầy đủ")
            !isValidEmail(email) ->
                return setRegisterError("Email không hợp lệ")
            phone != null && !isValidPhone(phone) ->
                return setRegisterError("Số điện thoại phải kèm mã quốc gia (ví dụ +84…)")
            form.password.length < MIN_PASSWORD_LENGTH ->
                return setRegisterError("Mật khẩu cần ít nhất $MIN_PASSWORD_LENGTH ký tự")
            form.password != form.confirmPassword ->
                return setRegisterError("Mật khẩu xác nhận không khớp")
            !form.acceptedTerms ->
                return setRegisterError("Vui lòng đồng ý với điều khoản sử dụng")
        }

        viewModelScope.launch {
            _uiState.update { it.copy(register = it.register.copy(isSubmitting = true, errorMessage = null)) }
            runCatching {
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = form.password
                    data = buildJsonMetadata(
                        fullName = fullName,
                        phoneNumber = phone,
                        role = form.role
                    )
                }
            }.onSuccess {
                val session = supabase.auth.currentSessionOrNull()
                _uiState.update {
                    it.copy(
                        register = it.register.copy(isSubmitting = false, password = "", confirmPassword = ""),
                        pendingRegistration = PendingRegistration(
                            email = email,
                            fullName = fullName,
                            phoneNumber = phone,
                            role = form.role
                        ),
                        otp = it.otp.copy(
                            otp = "",
                            sentTo = email,
                            method = AuthMethod.Email,
                            errorMessage = null
                        )
                    )
                }

                if (session != null) {
                    finalizeProfile(session.user?.id)
                    _uiState.update { it.copy(isAuthenticated = true) }
                    _events.tryEmit(AuthEvent.NavigateToHome)
                } else {
                    startResendCountdown()
                    _events.tryEmit(AuthEvent.NavigateToOtp)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(register = it.register.copy(isSubmitting = false)) }
                setRegisterError(parseSupabaseError(error))
            }
        }
    }

    // ─── OTP verification ─────────────────────────────────────────────────────

    fun onOtpChange(value: String) = _uiState.update {
        val digits = value.filter { c -> c.isDigit() }.take(OTP_LENGTH)
        it.copy(otp = it.otp.copy(otp = digits, errorMessage = null))
    }

    fun verifyOtp() {
        val otpState = _uiState.value.otp
        if (otpState.isVerifying) return
        if (otpState.otp.length != OTP_LENGTH) {
            setOtpError("Nhập đủ $OTP_LENGTH chữ số")
            return
        }
        val pending = _uiState.value.pendingRegistration
        if (pending == null) {
            setOtpError("Phiên đăng ký đã hết hạn. Vui lòng thử lại.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(otp = it.otp.copy(isVerifying = true, errorMessage = null)) }
            runCatching {
                supabase.auth.verifyEmailOtp(
                    type = OtpType.Email.SIGNUP,
                    email = pending.email,
                    token = otpState.otp
                )
            }.onSuccess {
                val userId = supabase.auth.currentSessionOrNull()?.user?.id
                finalizeProfile(userId)
                _uiState.update {
                    it.copy(
                        otp = it.otp.copy(isVerifying = false, otp = ""),
                        isAuthenticated = true,
                        pendingRegistration = null
                    )
                }
                _events.tryEmit(AuthEvent.NavigateToHome)
            }.onFailure { error ->
                _uiState.update { it.copy(otp = it.otp.copy(isVerifying = false)) }
                setOtpError(parseSupabaseError(error))
            }
        }
    }

    fun resendOtp() {
        val pending = _uiState.value.pendingRegistration ?: return
        if (_uiState.value.otp.isResending || _uiState.value.otp.resendSeconds > 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(otp = it.otp.copy(isResending = true, errorMessage = null)) }
            runCatching {
                supabase.auth.resendEmail(
                    type = OtpType.Email.SIGNUP,
                    email = pending.email
                )
            }.onSuccess {
                _uiState.update { it.copy(otp = it.otp.copy(isResending = false)) }
                startResendCountdown()
                _events.tryEmit(AuthEvent.Toast("Đã gửi lại mã xác thực"))
            }.onFailure { error ->
                _uiState.update { it.copy(otp = it.otp.copy(isResending = false)) }
                setOtpError(parseSupabaseError(error))
            }
        }
    }

    fun clearOtpState() {
        resendJob?.cancel()
        _uiState.update { it.copy(otp = OtpFormState()) }
    }

    // ─── Forgot password ──────────────────────────────────────────────────────

    fun onForgotEmailChange(value: String) = _uiState.update {
        it.copy(
            forgotPassword = it.forgotPassword.copy(
                email = value.trim(),
                errorMessage = null,
                successMessage = null
            )
        )
    }

    fun submitForgotPassword() {
        val form = _uiState.value.forgotPassword
        if (form.isSubmitting) return
        val email = form.email.trim().lowercase()
        if (!isValidEmail(email)) {
            _uiState.update {
                it.copy(forgotPassword = it.forgotPassword.copy(errorMessage = "Email không hợp lệ"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(forgotPassword = it.forgotPassword.copy(isSubmitting = true, errorMessage = null))
            }
            runCatching {
                supabase.auth.resetPasswordForEmail(email)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        forgotPassword = it.forgotPassword.copy(
                            isSubmitting = false,
                            successMessage = "Đã gửi liên kết đặt lại mật khẩu đến $email"
                        )
                    )
                }
                _events.tryEmit(AuthEvent.Toast("Kiểm tra email của bạn"))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        forgotPassword = it.forgotPassword.copy(
                            isSubmitting = false,
                            errorMessage = parseSupabaseError(error)
                        )
                    )
                }
            }
        }
    }

    // ─── Sign out ─────────────────────────────────────────────────────────────

    fun signOut() {
        viewModelScope.launch {
            runCatching { supabase.auth.signOut() }
            _uiState.update { AuthUiState(isBootstrapping = false) }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun finalizeProfile(userId: String?) {
        val pending = _uiState.value.pendingRegistration
        if (userId == null || pending == null) return
        profileRepository.upsertProfile(
            userId = userId,
            email = pending.email,
            fullName = pending.fullName,
            phoneNumber = pending.phoneNumber,
            role = pending.role
        ).onFailure { error ->
            Log.w(TAG, "upsertProfile failed: ${error.message}")
        }
    }

    private fun buildJsonMetadata(
        fullName: String,
        phoneNumber: String?,
        role: UserRole
    ) = kotlinx.serialization.json.buildJsonObject {
        put("full_name", kotlinx.serialization.json.JsonPrimitive(fullName))
        put("role", kotlinx.serialization.json.JsonPrimitive(role.name.lowercase()))
        if (!phoneNumber.isNullOrBlank()) {
            put("phone_number", kotlinx.serialization.json.JsonPrimitive(phoneNumber))
        }
    }

    private fun setLoginError(message: String) = _uiState.update {
        it.copy(login = it.login.copy(errorMessage = message))
    }

    private fun setRegisterError(message: String) = _uiState.update {
        it.copy(register = it.register.copy(errorMessage = message))
    }

    private fun setOtpError(message: String) = _uiState.update {
        it.copy(otp = it.otp.copy(errorMessage = message))
    }

    private fun startResendCountdown(seconds: Int = 30) {
        resendJob?.cancel()
        resendJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _uiState.update { it.copy(otp = it.otp.copy(resendSeconds = remaining)) }
                delay(1000L)
            }
            _uiState.update { it.copy(otp = it.otp.copy(resendSeconds = 0)) }
        }
    }

    private fun parseSupabaseError(error: Throwable): String {
        val raw = error.message ?: return "Đã có lỗi xảy ra. Vui lòng thử lại."
        val msg = raw
            .substringBefore("\nURL:")
            .substringBefore("URL:")
            .substringBefore("\nHeaders:")
            .trim()
        Log.d(TAG, "parseSupabaseError: cleaned='$msg' (raw length=${raw.length})")
        return when {
            msg.contains("invalid login credentials", ignoreCase = true) ->
                "Email/SĐT hoặc mật khẩu không đúng"
            msg.contains("email not confirmed", ignoreCase = true) ->
                "Email chưa được xác thực. Vui lòng kiểm tra hộp thư."
            msg.contains("user already registered", ignoreCase = true) ||
                msg.contains("already registered", ignoreCase = true) ->
                "Email này đã được đăng ký. Vui lòng đăng nhập."
            msg.contains("password should be", ignoreCase = true) ->
                "Mật khẩu không đáp ứng yêu cầu bảo mật"
            msg.contains("rate limit", ignoreCase = true) ||
                msg.contains("too many requests", ignoreCase = true) ->
                "Quá nhiều yêu cầu. Vui lòng đợi một lát."
            msg.contains("invalid", ignoreCase = true) && msg.contains("otp", ignoreCase = true) ->
                "Mã xác thực không đúng. Vui lòng thử lại."
            msg.contains("expired", ignoreCase = true) ->
                "Mã đã hết hạn. Vui lòng yêu cầu gửi lại."
            msg.contains("network", ignoreCase = true) ||
                msg.contains("connection", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ->
                "Lỗi kết nối. Vui lòng kiểm tra mạng và thử lại."
            msg.contains("provider", ignoreCase = true) && msg.contains("disabled", ignoreCase = true) ->
                "Phương thức đăng nhập này chưa được bật trên máy chủ."
            msg.contains("signup", ignoreCase = true) && msg.contains("disabled", ignoreCase = true) ->
                "Tính năng đăng ký tạm thời bị vô hiệu hóa."
            msg.contains("database error", ignoreCase = true) ->
                "Lỗi máy chủ: không thể lưu thông tin. Vui lòng thử lại sau."
            else -> msg.take(180)
        }
    }

    private fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else "+$digits"
    }

    private fun isValidPhone(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return phone.startsWith("+") && digits.length in 10..15
    }

    private fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)

    private fun detectAuthMethod(value: String): AuthMethod? {
        if (value.isBlank()) return null
        return if (value.contains("@") || value.any { it.isLetter() }) {
            AuthMethod.Email
        } else {
            AuthMethod.Phone
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"
        const val OTP_LENGTH = 6
        const val MIN_PASSWORD_LENGTH = 6
        val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
