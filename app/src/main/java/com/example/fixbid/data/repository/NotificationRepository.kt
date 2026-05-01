package com.example.fixbid.data.repository

import com.example.fixbid.data.supabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import com.example.fixbid.data.dto.NotificationDto

class NotificationRepository {
    suspend fun getMyNotifications(): Result<List<NotificationDto>> {
        return runCatching {
            supabase.postgrest["notifications"]
                .select {
                    filter { eq("is_read", false) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<NotificationDto>()
        }
    }
}