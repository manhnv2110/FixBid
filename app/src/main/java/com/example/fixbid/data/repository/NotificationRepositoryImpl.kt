package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.NotificationDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.domain.model.Notification
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : NotificationRepository {

    override suspend fun getNotifications(userId: String): Resource<List<Notification>> =
        runCatching {
            val result = client.postgrest[Tables.NOTIFICATIONS]
                .select(Columns.ALL) {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                    limit(20)                       // chỉ lấy 20 thông báo mới nhất
                }
                .decodeList<NotificationDto>()
            Resource.Success(result.map { it.toDomain() })
        }.getOrElse { Resource.Error(it.message ?: "Không thể tải thông báo") }

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

    override fun observeNotifications(userId: String): Flow<List<Notification>> {
        val channel = client.realtime.channel("notification_updates_$userId")

        return channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table  = Tables.NOTIFICATIONS
            filter("user_id", FilterOperator.EQ, userId)
        }.map {
            // Khi có thông báo mới, load lại toàn bộ
            (getNotifications(userId) as? Resource.Success)?.data ?: emptyList()
        }
    }

    override suspend fun saveFcmToken(userId: String, token: String): Resource<Unit> =
        runCatching {
            client.postgrest[Tables.FCM_TOKENS].upsert(
                mapOf("user_id" to userId, "token" to token)
            )
            Resource.Success(Unit)
        }.getOrElse { Resource.Error(it.message ?: "Lỗi lưu token") }
}