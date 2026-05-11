package com.example.fixbid.domain.usecase.shared

import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.NotificationRepository
import javax.inject.Inject

class GetNotificationsUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Resource<List<Notification>> {
        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")
        return notificationRepository.getNotifications(user.id)
    }
}