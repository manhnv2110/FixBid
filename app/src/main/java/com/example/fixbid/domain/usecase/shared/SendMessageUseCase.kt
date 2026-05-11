package com.example.fixbid.domain.usecase.shared

import com.example.fixbid.domain.model.Message
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.ChatRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(message: Message): Resource<Message> {
        if (message.content.isBlank() && message.imageUrl == null)
            return Resource.Error("Tin nhắn không được để trống")
        return chatRepository.sendMessage(message)
    }
}