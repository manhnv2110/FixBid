package com.example.fixbid.presentation.customer.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fixbid.core.chat.ActiveChatTracker
import com.example.fixbid.domain.model.ChatPresence
import com.example.fixbid.domain.model.Message
import com.example.fixbid.domain.model.MessageType
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.notification.NotificationContentFactory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.ChatRepository
import com.example.fixbid.domain.usecase.shared.SendNotificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ChatVM"

/** Quiet period after the last keystroke before we broadcast "stopped typing". */
private const val TYPING_DEBOUNCE_MS = 1_500L

/** Min interval between successive "started typing" broadcasts (rate-limit). */
private const val TYPING_BROADCAST_INTERVAL_MS = 2_000L

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isUploadingImage: Boolean = false,
    val inputText: String = "",
    val currentUserId: String = "",
    val errorMessage: String? = null,
    val counterpartAvatarUrl: String? = null,
    val presence: ChatPresence = ChatPresence(),
    /** True from when the video-call button is tapped until the call screen opens. */
    val isStartingCall: Boolean = false
)

sealed class ChatEvent {
    data class Toast(val message: String) : ChatEvent()
    object ScrollToBottom : ChatEvent()
}

/**
 * Drives the realtime chat screen.
 *
 * Pipeline:
 *  1. `observeMessages` (Postgres CDC, delta-applied) feeds the bubble list.
 *  2. `observePresence` (Realtime presence + typing broadcast) feeds the
 *     header — green dot, "Đang nhập…", "Hoạt động N phút trước".
 *  3. `sendMessage` runs an optimistic insert, awaits the server echo, then
 *     fires a NEW_MESSAGE notification to the recipient.
 *  4. `attachImage` uploads via Storage and sends an IMAGE-typed message.
 *  5. The screen is registered with [ActiveChatTracker] for the entire
 *     lifetime so [com.example.fixbid.presentation.notification.AppNotificationsViewModel]
 *     can suppress redundant push notifications while the user is looking
 *     at this thread.
 *
 * No polling. The previous fallback poll made every send round-trip costly
 * and masked Realtime bugs; the migration scripts (0009, 0010) ensure the
 * realtime publication is correctly configured so polling is no longer
 * required.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: com.example.fixbid.data.repository.ProfileRepository,
    private val sendNotification: SendNotificationUseCase,
    private val activeChatTracker: ActiveChatTracker,
    private val callRepository: com.example.fixbid.domain.repository.CallRepository,
    @com.example.fixbid.core.di.ApplicationScope private val applicationScope: kotlinx.coroutines.CoroutineScope,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: ""
    val workerId: String = savedStateHandle["workerId"] ?: ""
    val workerName: String = run {
        val raw = savedStateHandle.get<String>("workerName") ?: "Thợ"
        runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var realtimeJob: Job? = null
    private var presenceJob: Job? = null
    private var typingDebounceJob: Job? = null
    private var lastTypingBroadcastAt: Long = 0L
    private var senderName: String = ""

    init {
        if (conversationId.isNotBlank()) activeChatTracker.enter(conversationId)

        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.value = _uiState.value.copy(currentUserId = user?.id ?: "")
            senderName = user?.fullName ?: "Người dùng"
            markAsRead()

            if (workerId.isNotBlank()) {
                profileRepository.getProfile(workerId).onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(counterpartAvatarUrl = profile.avatarUrl)
                }
            }
        }
        observeRealtimeMessages()
        observePresence()
    }

    private fun observeRealtimeMessages() {
        if (conversationId.isBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collect { serverMessages ->
                updateMessages(serverMessages)
            }
        }
    }

    private fun observePresence() {
        viewModelScope.launch {
            val uid = _uiState.value.currentUserId
                .takeIf { it.isNotBlank() }
                ?: authRepository.getCurrentUser()?.id
                ?: return@launch
            if (conversationId.isBlank()) return@launch

            presenceJob?.cancel()
            presenceJob = viewModelScope.launch {
                chatRepository.observePresence(conversationId, uid).collect { presence ->
                    _uiState.value = _uiState.value.copy(presence = presence)
                }
            }
        }
    }

    /**
     * Merge server messages with any pending local (optimistic) entries so the
     * UI never blanks while the round-trip is in flight. Idempotent — equal
     * lists do not retrigger a state copy.
     */
    private fun updateMessages(serverMessages: List<Message>) {
        val pendingLocal = _uiState.value.messages.filter { local ->
            local.id.startsWith(LOCAL_PREFIX) &&
                serverMessages.none { m -> m.sameContent(local) }
        }
        val merged = (serverMessages + pendingLocal)
            .sortedBy { it.createdAt }
            .distinctBy { it.id }

        val current = _uiState.value.messages
        if (merged.size != current.size || merged != current || _uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(messages = merged, isLoading = false)
            if (merged.isNotEmpty()) {
                viewModelScope.launch { _events.emit(ChatEvent.ScrollToBottom) }
            }
        }
        markAsRead()
    }

    private fun Message.sameContent(other: Message): Boolean =
        senderId == other.senderId &&
            content == other.content &&
            kotlin.math.abs(createdAt - other.createdAt) < 60_000L

    private fun markAsRead() {
        val uid = _uiState.value.currentUserId
        if (uid.isBlank() || conversationId.isBlank()) return
        viewModelScope.launch {
            chatRepository.markAsRead(conversationId, uid)
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        scheduleTypingBroadcast(text)
    }

    /**
     * Throttle "started typing" broadcasts to one every [TYPING_BROADCAST_INTERVAL_MS]
     * and follow up with a single "stopped typing" after [TYPING_DEBOUNCE_MS] of
     * keyboard silence. This is the standard chat-app pattern and avoids spamming
     * the receiver every keystroke.
     */
    private fun scheduleTypingBroadcast(currentText: String) {
        val uid = _uiState.value.currentUserId.takeIf { it.isNotBlank() } ?: return
        if (conversationId.isBlank()) return

        if (currentText.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (now - lastTypingBroadcastAt > TYPING_BROADCAST_INTERVAL_MS) {
                lastTypingBroadcastAt = now
                viewModelScope.launch {
                    chatRepository.sendTypingIndicator(conversationId, uid, isTyping = true)
                }
            }
        }

        typingDebounceJob?.cancel()
        typingDebounceJob = viewModelScope.launch {
            delay(TYPING_DEBOUNCE_MS)
            chatRepository.sendTypingIndicator(conversationId, uid, isTyping = false)
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() || state.isSending) return
        if (conversationId.isBlank() || state.currentUserId.isBlank()) return

        // Cancel any pending typing-stopped broadcast — sending a message
        // implicitly stops typing, and we want the receiver to see that
        // immediately.
        typingDebounceJob?.cancel()
        viewModelScope.launch {
            chatRepository.sendTypingIndicator(conversationId, state.currentUserId, isTyping = false)
        }

        val now = System.currentTimeMillis()
        val optimistic = Message(
            id             = "$LOCAL_PREFIX$now",
            conversationId = conversationId,
            senderId       = state.currentUserId,
            recipientId    = recipientId(state.currentUserId),
            content        = text,
            type           = MessageType.TEXT,
            imageUrl       = null,
            isRead         = false,
            createdAt      = now
        )
        _uiState.value = state.copy(
            inputText = "",
            isSending = true,
            messages = (state.messages + optimistic).sortedBy { it.createdAt }
        )
        viewModelScope.launch { _events.emit(ChatEvent.ScrollToBottom) }

        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(optimistic.copy(id = ""))) {
                is Resource.Success -> {
                    val server = result.data
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        messages = _uiState.value.messages
                            .map { if (it.id == optimistic.id) server else it }
                            .distinctBy { it.id }
                            .sortedBy { it.createdAt }
                    )
                    notifyRecipient(text)
                }
                is Resource.Error -> {
                    Log.e(TAG, "sendMessage: failed ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        messages = _uiState.value.messages.filterNot { it.id == optimistic.id }
                    )
                    _events.emit(ChatEvent.Toast(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Pick + upload an image, then send an IMAGE message referencing it.
     * The screen passes raw bytes already loaded from the user's gallery.
     */
    fun sendImage(bytes: ByteArray, fileName: String) {
        val state = _uiState.value
        if (state.isUploadingImage || state.isSending) return
        if (conversationId.isBlank() || state.currentUserId.isBlank()) return

        _uiState.value = state.copy(isUploadingImage = true)
        viewModelScope.launch {
            when (val upload = chatRepository.uploadChatImage(conversationId, bytes, fileName)) {
                is Resource.Success -> {
                    val now = System.currentTimeMillis()
                    val msg = Message(
                        id             = "",
                        conversationId = conversationId,
                        senderId       = state.currentUserId,
                        recipientId    = recipientId(state.currentUserId),
                        content        = "",
                        type           = MessageType.IMAGE,
                        imageUrl       = upload.data,
                        isRead         = false,
                        createdAt      = now
                    )
                    when (val sendRes = chatRepository.sendMessage(msg)) {
                        is Resource.Success -> {
                            _uiState.value = _uiState.value.copy(isUploadingImage = false)
                            notifyRecipient("📷 Đã gửi 1 ảnh")
                        }
                        is Resource.Error -> {
                            _uiState.value = _uiState.value.copy(isUploadingImage = false)
                            _events.emit(ChatEvent.Toast(sendRes.message))
                        }
                        is Resource.Loading -> {}
                    }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isUploadingImage = false)
                    _events.emit(ChatEvent.Toast(upload.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Fan-out a NEW_MESSAGE notification to the recipient. We rely on the
     * existing notification pipeline (Realtime + push when configured) so
     * the recipient gets a heads-up bubble unless they're already on this
     * thread (in which case [ActiveChatTracker] suppresses the in-app
     * heads-up at the receiver side).
     */
    private fun notifyRecipient(preview: String) {
        val recipient = workerId.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            runCatching {
                sendNotification(
                    NotificationContentFactory.newMessageForRecipient(
                        recipientId = recipient,
                        conversationId = conversationId,
                        senderName = senderName,
                        preview = preview
                    )
                )
            }
        }
    }

    /**
     * The route always passes the *counterparty* id as `workerId`, so the
     * recipient is exactly that — we don't need to know whether we're the
     * customer or the worker side.
     */
    private fun recipientId(currentUserId: String): String =
        if (workerId.isNotBlank() && workerId != currentUserId) workerId else ""

    /**
     * Place a video call to [workerId]. Inserts a `ringing` row and
     * forwards the new call id to the navigator so the caller drops into
     * the live call screen. Backend realtime then surfaces the row to the
     * callee's [com.example.fixbid.presentation.call.IncomingCallController].
     */
    fun startVideoCall(onCallCreated: (callId: String) -> Unit) {
        val state = _uiState.value
        if (state.isStartingCall) return
        if (conversationId.isBlank() || workerId.isBlank()) return
        val currentUserId = state.currentUserId.ifBlank {
            // Cold path — typically already populated by observeRealtimeMessages,
            // but if the user taps the call button very quickly we still want to
            // resolve it before placing a call.
            null
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStartingCall = true, errorMessage = null)
            val resolvedCaller = currentUserId ?: authRepository.getCurrentUser()?.id
            if (resolvedCaller.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(isStartingCall = false)
                _events.tryEmit(ChatEvent.Toast("Không xác định được người dùng"))
                return@launch
            }
            when (val res = callRepository.startCall(
                conversationId = conversationId,
                callerId = resolvedCaller,
                calleeId = workerId
            )) {
                is com.example.fixbid.domain.model.Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isStartingCall = false)
                    onCallCreated(res.data.id)
                }
                is com.example.fixbid.domain.model.Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isStartingCall = false)
                    _events.tryEmit(ChatEvent.Toast("Không gọi được: ${res.message}"))
                }
                is com.example.fixbid.domain.model.Resource.Loading -> Unit
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (conversationId.isNotBlank()) activeChatTracker.leave(conversationId)
        realtimeJob?.cancel()
        presenceJob?.cancel()
        typingDebounceJob?.cancel()
        // Best-effort "I left" typing pulse — the presence channel auto-emits
        // a leave event when the WebSocket closes, but stop typing too. We
        // hop to the long-lived application scope because viewModelScope is
        // already cancelling at this point.
        val uid = _uiState.value.currentUserId
        if (uid.isNotBlank() && conversationId.isNotBlank()) {
            applicationScope.launch {
                runCatching {
                    chatRepository.sendTypingIndicator(conversationId, uid, isTyping = false)
                }
            }
        }
    }

    private companion object {
        const val LOCAL_PREFIX = "local_"
    }
}
