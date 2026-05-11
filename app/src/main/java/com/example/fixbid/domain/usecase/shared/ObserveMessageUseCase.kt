package com.example.fixbid.domain.usecase.shared

import com.example.fixbid.domain.model.Message
import com.example.fixbid.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(conversationId: String): Flow<List<Message>> =
        chatRepository.observeMessages(conversationId)
}