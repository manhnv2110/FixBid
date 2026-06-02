package com.example.fixbid.presentation.worker.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.data.local.UserPreferencesDataStore
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.usecase.worker.GetMyWorkerProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Identity verification status for a worker.
 *
 *  - [NOT_SUBMITTED] — worker has never submitted documents.
 *  - [PENDING] — submission stored locally, awaiting manual review by ops.
 *  - [VERIFIED] — backend has set `worker_profiles.identity_verified = true`.
 */
enum class VerificationStatus { NOT_SUBMITTED, PENDING, VERIFIED }

data class VerifyIdentityUiState(
    val isLoading: Boolean = true,
    val status: VerificationStatus = VerificationStatus.NOT_SUBMITTED,
    val submittedAt: Long? = null,
    val frontUri: Uri? = null,
    val backUri: Uri? = null,
    val selfieUri: Uri? = null,
    val fullName: String = "",
    val idNumber: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    /** Whether all document slots and required fields are filled. */
    val canSubmit: Boolean
        get() = !isSubmitting &&
            status != VerificationStatus.VERIFIED &&
            frontUri != null &&
            backUri != null &&
            selfieUri != null &&
            fullName.trim().length >= 2 &&
            idNumber.filter(Char::isDigit).length in 9..12
}

sealed interface VerifyIdentityEvent {
    data class Toast(val message: String) : VerifyIdentityEvent
    data object Submitted : VerifyIdentityEvent
}

/**
 * Drives the worker identity verification flow.
 *
 * Backend integration is intentionally limited: there is no `submit-id`
 * endpoint yet, and `worker_profiles.identity_verified` is server-managed.
 * To still give the worker a complete UX, the screen records the submission
 * timestamp locally in [UserPreferencesDataStore]. The next time the screen
 * loads, that timestamp combined with the server's `identityVerified` flag
 * tells us whether the request is still pending or has been approved.
 */
@HiltViewModel
class WorkerVerifyIdentityViewModel @Inject constructor(
    private val getMyWorkerProfileUseCase: GetMyWorkerProfileUseCase,
    private val preferences: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyIdentityUiState())
    val uiState: StateFlow<VerifyIdentityUiState> = _uiState.asStateFlow()

    private val _events = Channel<VerifyIdentityEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val submittedAt = preferences.verificationSubmittedAt.first()
            val status = when (val res = getMyWorkerProfileUseCase()) {
                is Resource.Success -> when {
                    res.data.identityVerified -> VerificationStatus.VERIFIED
                    submittedAt != null -> VerificationStatus.PENDING
                    else -> VerificationStatus.NOT_SUBMITTED
                }
                is Resource.Error -> {
                    if (submittedAt != null) VerificationStatus.PENDING
                    else VerificationStatus.NOT_SUBMITTED
                }
                is Resource.Loading -> VerificationStatus.NOT_SUBMITTED
            }
            // Once approved on the server, drop the stale local timestamp.
            if (status == VerificationStatus.VERIFIED && submittedAt != null) {
                preferences.clearVerificationSubmission()
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    status = status,
                    submittedAt = submittedAt
                )
            }
        }
    }

    fun onFrontSelected(uri: Uri?) = _uiState.update { it.copy(frontUri = uri) }
    fun onBackSelected(uri: Uri?) = _uiState.update { it.copy(backUri = uri) }
    fun onSelfieSelected(uri: Uri?) = _uiState.update { it.copy(selfieUri = uri) }
    fun onFullNameChange(value: String) = _uiState.update { it.copy(fullName = value) }
    fun onIdNumberChange(value: String) = _uiState.update {
        it.copy(idNumber = value.filter(Char::isDigit).take(12))
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            // Backend submission endpoint is not available yet, so we record
            // the local timestamp and surface a "đang xét duyệt" state. When
            // the endpoint lands, swap this for a real upload + API call.
            val now = System.currentTimeMillis()
            runCatching { preferences.markVerificationSubmitted(now) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            status = VerificationStatus.PENDING,
                            submittedAt = now,
                            // Keep the URIs visible so the worker sees what
                            // they submitted while waiting for review.
                        )
                    }
                    _events.send(VerifyIdentityEvent.Toast("Đã gửi yêu cầu xác minh"))
                    _events.send(VerifyIdentityEvent.Submitted)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "Không thể gửi yêu cầu, vui lòng thử lại"
                        )
                    }
                }
        }
    }

    /** Exposed so the worker can re-submit after a rejection in the future. */
    fun reset() {
        viewModelScope.launch {
            preferences.clearVerificationSubmission()
            _uiState.update {
                it.copy(
                    status = VerificationStatus.NOT_SUBMITTED,
                    submittedAt = null,
                    frontUri = null,
                    backUri = null,
                    selfieUri = null,
                    fullName = "",
                    idNumber = "",
                    errorMessage = null
                )
            }
        }
    }
}
