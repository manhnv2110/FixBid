package com.example.fixbid.presentation.customer.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.data.repository.ProfileRepository
import com.example.fixbid.domain.model.Conversation
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.User
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConversationListUiState {
    object Loading : ConversationListUiState()
    data class Success(val conversations: List<Conversation>) : ConversationListUiState()
    data class Error(val message: String) : ConversationListUiState()
}

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversationListUiState>(ConversationListUiState.Loading)
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Tổng số tin nhắn chưa đọc — dùng cho badge bottom bar */
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private var currentUserId: String? = null
    private var hasLoadedOnce = false

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            if (!hasLoadedOnce) {
                _uiState.value = ConversationListUiState.Loading
            }
            fetchConversations()
            hasLoadedOnce = true
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchConversations()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchConversations() {
        val user = authRepository.getCurrentUser()
        currentUserId = user?.id ?: return

        when (val result = chatRepository.getConversations(currentUserId!!)) {
            is Resource.Success -> {
                // Enrich mỗi conversation với thông tin worker (tên, avatar) từ profiles table
                val enriched = result.data.map { conv ->
                    val profileResult = profileRepository.getProfile(conv.workerId)
                    if (profileResult.isSuccess) {
                        val profileDto = profileResult.getOrNull()
                        conv.copy(
                            worker = if (profileDto != null) User(
                                id          = profileDto.id,
                                email       = profileDto.email ?: "",
                                fullName    = profileDto.fullName,
                                phoneNumber = profileDto.phoneNumber,
                                avatarUrl   = profileDto.avatarUrl,
                                role        = UserRole.WORKER,
                                createdAt   = 0L
                            ) else null
                        )
                    } else conv
                }
                // Sort: conversation mới nhất lên trên
                val sorted = enriched.sortedByDescending {
                    it.lastMessage?.createdAt ?: it.createdAt
                }
                _uiState.value = ConversationListUiState.Success(sorted)
                _unreadCount.value = sorted.sumOf { it.unreadCount }
            }
            is Resource.Error -> {
                if (_uiState.value !is ConversationListUiState.Success) {
                    _uiState.value = ConversationListUiState.Error(result.message)
                }
            }
            is Resource.Loading -> {}
        }
    }
}
