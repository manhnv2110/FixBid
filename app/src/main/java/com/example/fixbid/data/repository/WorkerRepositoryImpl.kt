package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.WorkerProfileDto
import com.example.fixbid.data.remote.dto.toDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.WorkerRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class WorkerRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : WorkerRepository {

    override suspend fun getWorkers(
        category: ServiceCategory?,
        query: String?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double?,
        minRating: Double?,
        maxPricePerHour: Double?,
        page: Int,
        pageSize: Int
    ): Resource<List<WorkerProfile>> = runCatching {
        val result = client.postgrest[Tables.WORKER_PROFILES]
            .select(Columns.ALL) {
                filter {
                    eq("is_available", true)
                    minRating?.let { gte("average_rating", it) }
                    maxPricePerHour?.let { lte("price_per_hour", it) }
                    // Filter array: dùng cs (contains) với cú pháp đúng cho v1
                    category?.let {
                        filter("skills", FilterOperator.CS, "{${it.name.lowercase()}}")
                    }
                }
                order("average_rating", Order.DESCENDING)
                range(
                    from = (page * pageSize).toLong(),
                    to   = ((page + 1) * pageSize - 1).toLong()
                )
            }
            .decodeList<WorkerProfileDto>()
        Resource.Success(result.map { it.toDomain() })
    }.getOrElse { Resource.Error(it.message ?: "Lỗi tải danh sách thợ") }

    override suspend fun getWorkerById(workerId: String): Resource<WorkerProfile> =
        runCatching {
            val result = client.postgrest[Tables.WORKER_PROFILES]
                .select(Columns.ALL) { filter { eq("user_id", workerId) } }
                .decodeSingle<WorkerProfileDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Không tìm thấy thợ") }

    override suspend fun updateWorkerProfile(
        profile: WorkerProfile
    ): Resource<WorkerProfile> = runCatching {
        client.postgrest[Tables.WORKER_PROFILES]
            .update(profile.toDto()) {
                filter { eq("user_id", profile.userId) }
            }
        Resource.Success(profile)
    }.getOrElse { Resource.Error(it.message ?: "Cập nhật thất bại") }

    override suspend fun setAvailability(isAvailable: Boolean): Resource<Unit> =
        runCatching {
            val userId = client.auth.currentSessionOrNull()?.user?.id
                ?: return Resource.Error("Chưa đăng nhập")
            client.postgrest[Tables.WORKER_PROFILES]
                .update(buildJsonObject { put("is_available", isAvailable) }) {
                    filter { eq("user_id", userId) }
                }
            Resource.Success(Unit)
        }.getOrElse { Resource.Error(it.message ?: "Lỗi") }

    override fun observeWorkerProfile(workerId: String): Flow<WorkerProfile?> {
        val channel = client.realtime.channel("worker_profile_$workerId")
        return channel
            .postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = Tables.WORKER_PROFILES
                filter("user_id", FilterOperator.EQ, workerId)
            }
            .map { action ->
                runCatching {
                    action.decodeRecord<WorkerProfileDto>().toDomain()
                }.getOrNull()
            }
    }
}