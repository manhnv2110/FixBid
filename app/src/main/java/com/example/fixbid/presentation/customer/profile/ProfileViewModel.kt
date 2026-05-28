package com.example.fixbid.presentation.customer.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.data.local.UserPreferencesDataStore
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.User
import com.example.fixbid.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val editFullName: String = "",
    val editPhone: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    val appTheme: Flow<String> = userPreferencesDataStore.appTheme

    fun saveTheme(theme: String) {
        viewModelScope.launch {
            userPreferencesDataStore.saveTheme(theme)
        }
    }

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val user = authRepository.getCurrentUser()
            _uiState.value = _uiState.value.copy(
                user = user,
                isLoading = false,
                editFullName = user?.fullName ?: "",
                editPhone = user?.phoneNumber ?: ""
            )
        }
    }

    fun startEditing() {
        val user = _uiState.value.user ?: return
        _uiState.value = _uiState.value.copy(
            isEditing = true,
            editFullName = user.fullName,
            editPhone = user.phoneNumber ?: "",
            errorMessage = null,
            successMessage = null
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(
            isEditing = false,
            errorMessage = null
        )
    }

    fun onFullNameChange(value: String) {
        _uiState.value = _uiState.value.copy(editFullName = value, errorMessage = null)
    }

    fun onPhoneChange(value: String) {
        _uiState.value = _uiState.value.copy(editPhone = value, errorMessage = null)
    }

    fun saveProfile() {
        val state = _uiState.value
        val user = state.user ?: return

        if (state.editFullName.trim().length < 2) {
            _uiState.value = state.copy(errorMessage = "Họ tên phải có ít nhất 2 ký tự")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            val updatedUser = user.copy(
                fullName = state.editFullName.trim(),
                phoneNumber = state.editPhone.trim().ifBlank { null }
            )
            when (val result = authRepository.updateProfile(updatedUser)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        user = updatedUser,
                        isEditing = false,
                        isSaving = false,
                        successMessage = "Cập nhật thành công"
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = result.message
                    )
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
