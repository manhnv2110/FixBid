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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConversationListUiState {
    object Loading : ConversationListUiState()
    data class Success(
        val conversations: List<Conversation>,
        val currentUserId: String
    ) : ConversationListUiState()
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
    private var realtimeJob: Job? = null

    init {
        loadConversations()
        observeRealtime()
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

    /** Live-refresh the list whenever any message changes (new msg / read state). */
    private fun observeRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: return@launch
            currentUserId = user.id
            chatRepository.observeConversations(user.id).collect { conversations ->
                emitEnriched(conversations, user.id)
            }
        }
    }

    private suspend fun fetchConversations() {
        val user = authRepository.getCurrentUser()
        currentUserId = user?.id ?: return

        when (val result = chatRepository.getConversations(user.id)) {
            is Resource.Success -> emitEnriched(result.data, user.id)
            is Resource.Error -> {
                if (_uiState.value !is ConversationListUiState.Success) {
                    _uiState.value = ConversationListUiState.Error(result.message)
                }
            }
            is Resource.Loading -> {}
        }
    }

    /**
     * Enrich each conversation with the COUNTERPARTY's profile (so a customer sees
     * the worker, and a worker sees the customer — never themselves), then sort by
     * most recent activity.
     */
    private suspend fun emitEnriched(conversations: List<Conversation>, userId: String) {
        val enriched = conversations.map { conv ->
            val otherId = conv.counterpartId(userId)
            val dto = profileRepository.getProfile(otherId).getOrNull()
            val person = dto?.let {
                User(
                    id = it.id,
                    email = it.email ?: "",
                    fullName = it.fullName,
                    phoneNumber = it.phoneNumber,
                    avatarUrl = it.avatarUrl,
                    role = it.role,
                    createdAt = 0L
                )
            }
            // Put the counterpart into the slot that represents "the other person".
            if (userId == conv.customerId) conv.copy(worker = person)
            else conv.copy(customer = person)
        }.sortedByDescending { it.lastMessage?.createdAt ?: it.createdAt }

        _uiState.value = ConversationListUiState.Success(enriched, userId)
        _unreadCount.value = enriched.sumOf { it.unreadCount }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}
