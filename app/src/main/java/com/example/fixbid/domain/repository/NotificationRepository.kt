package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.Resource
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    suspend fun getNotifications(userId: String): Resource<List<Notification>>
    suspend fun markAsRead(notificationId: String): Resource<Unit>
    suspend fun markAllAsRead(userId: String): Resource<Unit>
    fun observeNotifications(userId: String): Flow<List<Notification>>
    suspend fun saveFcmToken(userId: String, token: String): Resource<Unit>
}