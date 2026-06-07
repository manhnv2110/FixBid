package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.NewNotificationDto
import com.example.fixbid.data.remote.dto.NotificationDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.data.remote.supabase.liveFlow
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.NotificationType
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
private data class UnreadIdRow(val id: String = "")

class NotificationRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : NotificationRepository {

    override suspend fun getNotifications(userId: String): Resource<List<Notification>> =
        runCatching {
            val result = client.postgrest[Tables.NOTIFICATIONS]
                .select(Columns.ALL) {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                    limit(50)                       // lấy 50 thông báo mới nhất cho lịch sử
                }
                .decodeList<NotificationDto>()
            Resource.Success(result.map { it.toDomain() })
        }.getOrElse { Resource.Error(it.message ?: "Không thể tải thông báo") }

    override suspend fun getUnreadCount(userId: String): Resource<Int> =
        runCatching {
            val unread = client.postgrest[Tables.NOTIFICATIONS]
                .select(Columns.list("id")) {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                }
                .decodeList<UnreadIdRow>()
            Resource.Success(unread.size)
        }.getOrElse { Resource.Error(it.message ?: "Lỗi đếm thông báo") }

    override suspend fun markAsRead(notificationId: String): Resource<Unit> =
        runCatching {
            client.postgrest[Tables.NOTIFICATIONS]
                .update(mapOf("is_read" to true)) { filter { eq("id", notificationId) } }
            Resource.Success(Unit)
        }.getOrElse { Resource.Error(it.message ?: "Lỗi") }

    override suspend fun markAllAsRead(userId: String): Resource<Unit> =
        runCatching {
            client.postgrest[Tables.NOTIFICATIONS]
                .update(mapOf("is_read" to true)) {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                }
            Resource.Success(Unit)
        }.getOrElse { Resource.Error(it.message ?: "Lỗi") }

    override suspend fun createNotification(
        userId: String,
        title: String,
        body: String,
        type: NotificationType,
        referenceId: String?
    ): Resource<Unit> = runCatching {
        client.postgrest[Tables.NOTIFICATIONS].insert(
            NewNotificationDto(
                userId      = userId,
                title       = title,
                body        = body,
                type        = type.dbValue,
                referenceId = referenceId
            )
        )
        Resource.Success(Unit)
    }.getOrElse { Resource.Error(it.message ?: "Gửi thông báo thất bại") }

    override fun observeNotifications(userId: String): Flow<List<Notification>> {
        val channel = client.realtime.channel("notification_updates_${userId}_${System.currentTimeMillis()}")

        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table  = Tables.NOTIFICATIONS
            filter("user_id", FilterOperator.EQ, userId)
        }.map {
            // Khi có thông báo mới, load lại toàn bộ
            (getNotifications(userId) as? Resource.Success)?.data ?: emptyList()
        }
        return channel.liveFlow(changes)
    }

    override fun observeNewNotifications(userId: String): Flow<Notification> {
        val channel = client.realtime.channel("notification_push_${userId}_${System.currentTimeMillis()}")

        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table  = Tables.NOTIFICATIONS
            filter("user_id", FilterOperator.EQ, userId)
        }.mapNotNull { action ->
            runCatching { action.decodeRecord<NotificationDto>().toDomain() }.getOrNull()
        }
        return channel.liveFlow(changes)
    }

    override suspend fun saveFcmToken(userId: String, token: String): Resource<Unit> =
        runCatching {
            // Conflict-resolve on the unique `token` column so a re-install or
            // a different user signing in on the same device overwrites the
            // existing row instead of inserting a duplicate. The unique
            // constraint is added by migration 0006_fcm_tokens_unique.sql.
            client.postgrest[Tables.FCM_TOKENS].upsert(
                value = mapOf("user_id" to userId, "token" to token)
            ) {
                onConflict = "token"
            }
            Resource.Success(Unit)
        }.getOrElse { Resource.Error(it.message ?: "Lỗi lưu token") }
}
