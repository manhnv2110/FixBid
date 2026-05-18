package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.BookingDto
import com.example.fixbid.data.remote.dto.toDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BookingRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class BookingRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : BookingRepository {

    override suspend fun createDirectBooking(booking: Booking): Resource<Booking> =
        runCatching {
            val dto = booking.toDto().copy(type = "direct", status = "pending")
            val result = client.postgrest[Tables.BOOKINGS]
                .insert(dto) { select(Columns.ALL) }
                .decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Tạo booking thất bại") }

    override suspend fun createBiddingBooking(booking: Booking): Resource<Booking> =
        runCatching {
            val dto = booking.toDto().copy(type = "bidding", status = "bidding")
            val result = client.postgrest[Tables.BOOKINGS]
                .insert(dto) { select(Columns.ALL) }
                .decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Tạo yêu cầu thầu thất bại") }

    override suspend fun getCustomerBookings(
        customerId: String,
        status: BookingStatus?
    ): Resource<List<Booking>> = runCatching {
        val result = client.postgrest[Tables.BOOKINGS].select(Columns.ALL) {
            filter {
                eq("customer_id", customerId)
                status?.let { eq("status", it.name.lowercase()) }
            }
            // Sửa cú pháp order thành DESCENDING
            order("created_at", Order.DESCENDING)
        }.decodeList<BookingDto>()
        Resource.Success(result.map { it.toDomain() })
    }.getOrElse { Resource.Error(it.message ?: "Lỗi tải danh sách booking") }

    override suspend fun getWorkerBookings(
        workerId: String,
        status: BookingStatus?
    ): Resource<List<Booking>> = runCatching {
        val result = client.postgrest[Tables.BOOKINGS].select(Columns.ALL) {
            filter {
                eq("worker_id", workerId)
                status?.let { eq("status", it.name.lowercase()) }
            }
            // Sửa cú pháp order thành ASCENDING
            order("scheduled_at", Order.ASCENDING)
        }.decodeList<BookingDto>()
        Resource.Success(result.map { it.toDomain() })
    }.getOrElse { Resource.Error(it.message ?: "Lỗi tải danh sách công việc") }

    override suspend fun getOpenJobRequests(
        categories: List<com.example.fixbid.domain.model.ServiceCategory>?,
        excludeBookingIds: List<String>
    ): Resource<List<Booking>> = runCatching {
        val result = client.postgrest[Tables.BOOKINGS].select(Columns.ALL) {
            filter {
                eq("type", "bidding")
                eq("status", "bidding")
                filter("worker_id", FilterOperator.IS, "null")
                if (!categories.isNullOrEmpty()) {
                    val csv = categories.joinToString(",") { it.name.lowercase() }
                    filter("category", FilterOperator.IN, "($csv)")
                }
            }
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeList<BookingDto>()
        val excludeSet = excludeBookingIds.toSet()
        Resource.Success(result.map { it.toDomain() }.filter { it.id !in excludeSet })
    }.getOrElse { Resource.Error(it.message ?: "Lỗi tải danh sách yêu cầu") }

    override suspend fun confirmBooking(bookingId: String): Resource<Booking> =
        updateStatus(bookingId, BookingStatus.CONFIRMED)

    override suspend fun startJob(bookingId: String): Resource<Booking> =
        updateStatus(bookingId, BookingStatus.IN_PROGRESS)

    override suspend fun completeJob(bookingId: String, workerNote: String?): Resource<Booking> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject {
                    put("status", "completed")
                    workerNote?.let { put("worker_note", it) }
                    put("updated_at", System.currentTimeMillis().toString())
                }
            ) {
                filter { eq("id", bookingId) }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Lỗi") }

    override suspend fun cancelBooking(bookingId: String, reason: String): Resource<Unit> =
        runCatching {
            client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject { put("status", "cancelled"); put("customer_note", reason) }
            ) { filter { eq("id", bookingId) } }
            Resource.Success(Unit)
        }.getOrElse { Resource.Error(it.message ?: "Hủy thất bại") }

    override suspend fun getBookingById(bookingId: String): Resource<Booking> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS]
                .select(Columns.ALL) { filter { eq("id", bookingId) } }
                .decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Không tìm thấy booking") }

    override fun observeBooking(bookingId: String): Flow<Booking?> {
        val channel = client.realtime.channel("booking_updates_$bookingId")

        return channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = Tables.BOOKINGS
            filter("id", FilterOperator.EQ, bookingId)
        }.map { action ->
            runCatching { action.decodeRecord<BookingDto>().toDomain() }.getOrNull()
        }
    }

    private suspend fun updateStatus(bookingId: String, status: BookingStatus): Resource<Booking> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject { put("status", status.name.lowercase()) }
            ) {
                filter { eq("id", bookingId) }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Cập nhật thất bại") }
}