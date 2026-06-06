package com.example.fixbid.presentation.worker.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.usecase.worker.GetMyWorkerProfileUseCase
import com.example.fixbid.domain.usecase.worker.UpdateMyWorkerProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkerProfileEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val avatarUrl: String? = null,
    val existing: WorkerProfile? = null,
    val bio: String = "",
    val selectedSkills: Set<ServiceCategory> = emptySet(),
    val experienceYears: String = "",
    val pricePerHour: String = "",
    val location: String = "",
    val averageRating: Double = 0.0,
    val totalReviews: Int = 0,
    val totalJobsDone: Int = 0,
    val identityVerified: Boolean = false,
    val errorMessage: String? = null
)

sealed interface WorkerProfileEditEvent {
    data class Toast(val message: String) : WorkerProfileEditEvent
    data object Saved : WorkerProfileEditEvent
}

@HiltViewModel
class WorkerProfileEditViewModel @Inject constructor(
    private val getMyWorkerProfileUseCase: GetMyWorkerProfileUseCase,
    private val updateMyWorkerProfileUseCase: UpdateMyWorkerProfileUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerProfileEditUiState())
    val uiState: StateFlow<WorkerProfileEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<WorkerProfileEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = getMyWorkerProfileUseCase()) {
                is Resource.Success -> {
                    val p = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            existing = p,
                            bio = p.bio,
                            selectedSkills = p.skills.toSet(),
                            experienceYears = if (p.experienceYears > 0) p.experienceYears.toString() else "",
                            pricePerHour = if (p.pricePerHour > 0) p.pricePerHour.toLong().toString() else "",
                            location = p.location,
                            averageRating = p.averageRating,
                            totalReviews = p.totalReviews,
                            totalJobsDone = p.totalJobsDone,
                            identityVerified = p.identityVerified
                        )
                    }
                    // Load avatar từ user profile
                    val user = authRepository.getCurrentUser()
                    _uiState.update { it.copy(avatarUrl = user?.avatarUrl) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun onBioChange(value: String) = _uiState.update { it.copy(bio = value) }
    fun onExperienceChange(value: String) = _uiState.update {
        it.copy(experienceYears = value.filter(Char::isDigit).take(2))
    }
    fun onPriceChange(value: String) = _uiState.update {
        it.copy(pricePerHour = value.filter(Char::isDigit).take(9))
    }
    fun onLocationChange(value: String) = _uiState.update { it.copy(location = value) }

    fun toggleSkill(skill: ServiceCategory) = _uiState.update { state ->
        val next = state.selectedSkills.toMutableSet().apply {
            if (contains(skill)) remove(skill) else add(skill)
        }
        state.copy(selectedSkills = next)
    }

    fun save() {
        val s = _uiState.value
        if (s.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = updateMyWorkerProfileUseCase(
                bio = s.bio,
                skills = s.selectedSkills.toList(),
                experienceYears = s.experienceYears.toIntOrNull() ?: 0,
                pricePerHour = s.pricePerHour.toDoubleOrNull() ?: 0.0,
                location = s.location,
                isAvailable = s.existing?.isAvailable ?: true,
                existing = s.existing
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, existing = result.data) }
                    _events.send(WorkerProfileEditEvent.Toast("Đã lưu hồ sơ"))
                    _events.send(WorkerProfileEditEvent.Saved)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
                    _events.send(WorkerProfileEditEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun uploadAvatar(imageBytes: ByteArray) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            _uiState.update { it.copy(isUploadingAvatar = true) }
            val fileName = "avatar_${System.currentTimeMillis()}.jpg"
            when (val result = authRepository.uploadAvatar(userId, imageBytes, fileName)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isUploadingAvatar = false, avatarUrl = result.data) }
                    _events.send(WorkerProfileEditEvent.Toast("Đã cập nhật ảnh đại diện"))
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isUploadingAvatar = false, errorMessage = result.message) }
                    _events.send(WorkerProfileEditEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }
}
