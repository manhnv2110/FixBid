package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.NotificationType
import com.example.fixbid.domain.model.Resource
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    suspend fun getNotifications(userId: String): Resource<List<Notification>>
    suspend fun getUnreadCount(userId: String): Resource<Int>
    suspend fun markAsRead(notificationId: String): Resource<Unit>
    suspend fun markAllAsRead(userId: String): Resource<Unit>

    /** Tạo một thông báo gửi tới [userId]. Dùng bởi các luồng booking/bid/công việc. */
    suspend fun createNotification(
        userId: String,
        title: String,
        body: String,
        type: NotificationType,
        referenceId: String? = null
    ): Resource<Unit>

    fun observeNotifications(userId: String): Flow<List<Notification>>

    /** Phát ra từng thông báo mới ngay khi nó được chèn (dùng để bắn push cục bộ). */
    fun observeNewNotifications(userId: String): Flow<Notification>

    suspend fun saveFcmToken(userId: String, token: String): Resource<Unit>
}
