package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.BookingDto
import com.example.fixbid.data.remote.dto.toDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.data.remote.supabase.liveFlow
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
import io.github.jan.supabase.storage.storage
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

    override suspend fun getPendingDirectBookings(workerId: String): Resource<List<Booking>> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS].select(Columns.ALL) {
                filter {
                    eq("worker_id", workerId)
                    eq("type", "direct")
                    eq("status", "pending")
                }
                order("created_at", Order.DESCENDING)
                limit(50)
            }.decodeList<BookingDto>()
            Resource.Success(result.map { it.toDomain() })
        }.getOrElse { Resource.Error(it.message ?: "Lỗi tải yêu cầu trực tiếp") }

    override suspend fun acceptDirectBooking(bookingId: String): Resource<Booking> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject {
                    put("status", "awaiting_payment")
                    put("updated_at", java.time.Instant.now().toString())
                }
            ) {
                filter { eq("id", bookingId) }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Nhận đơn thất bại") }

    override suspend fun declineDirectBooking(
        bookingId: String,
        reason: String
    ): Resource<Booking> = runCatching {
        val result = client.postgrest[Tables.BOOKINGS].update(
            buildJsonObject {
                put("status", "cancelled")
                // Use the dedicated cancel_reason column so the customer's
                // original customer_note (containing phone/name/address) stays
                // intact for follow-up support.
                put("cancel_reason", reason)
                put("updated_at", java.time.Instant.now().toString())
            }
        ) {
            filter { eq("id", bookingId) }
            select(Columns.ALL)
        }.decodeSingle<BookingDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Từ chối đơn thất bại") }

    override suspend fun confirmBooking(bookingId: String): Resource<Booking> =
        updateStatus(bookingId, BookingStatus.CONFIRMED)

    override suspend fun startJob(bookingId: String): Resource<Booking> =
        updateStatus(bookingId, BookingStatus.IN_PROGRESS)

    override suspend fun completeJob(bookingId: String, workerNote: String?): Resource<Booking> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject {
                    put("status", "pending_completion")
                    workerNote?.let { put("worker_note", it) }
                    put("updated_at", java.time.Instant.now().toString())
                }
            ) {
                filter { eq("id", bookingId) }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Lỗi") }

    override suspend fun submitJobCompletion(
        bookingId: String,
        completionNote: String?,
        completionImageUrls: List<String>
    ): Resource<Booking> = runCatching {
        val result = client.postgrest[Tables.BOOKINGS].update(
            buildJsonObject {
                put("status", "pending_completion")
                completionNote?.let { put("completion_note", it) }
                put("completion_images", kotlinx.serialization.json.JsonArray(
                    completionImageUrls.map { kotlinx.serialization.json.JsonPrimitive(it) }
                ))
                put("updated_at", java.time.Instant.now().toString())
            }
        ) {
            filter { eq("id", bookingId) }
            select(Columns.ALL)
        }.decodeSingle<BookingDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Gửi hoàn thành thất bại") }

    override suspend fun uploadCompletionImage(
        bookingId: String,
        imageBytes: ByteArray,
        fileName: String
    ): Resource<String> = runCatching {
        val path = "completions/$bookingId/$fileName"
        val bucket = client.storage.from("booking-images")
        bucket.upload(path, imageBytes) { upsert = true }
        val publicUrl = bucket.publicUrl(path)
        Resource.Success(publicUrl)
    }.getOrElse { Resource.Error(it.message ?: "Upload ảnh thất bại") }

    override suspend fun uploadDescriptionImage(
        bookingId: String,
        imageBytes: ByteArray,
        fileName: String
    ): Resource<String> = runCatching {
        val path = "descriptions/$bookingId/$fileName"
        val bucket = client.storage.from("booking-images")
        bucket.upload(path, imageBytes) { upsert = true }
        val publicUrl = bucket.publicUrl(path)
        Resource.Success(publicUrl)
    }.getOrElse { Resource.Error(it.message ?: "Upload ảnh mô tả thất bại") }

    override suspend fun updateDescriptionImages(
        bookingId: String,
        imageUrls: List<String>
    ): Resource<Booking> = runCatching {
        val result = client.postgrest[Tables.BOOKINGS].update(
            buildJsonObject {
                put("description_images", kotlinx.serialization.json.JsonArray(
                    imageUrls.map { kotlinx.serialization.json.JsonPrimitive(it) }
                ))
                put("updated_at", java.time.Instant.now().toString())
            }
        ) {
            filter { eq("id", bookingId) }
            select(Columns.ALL)
        }.decodeSingle<BookingDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Cập nhật ảnh mô tả thất bại") }

    override suspend fun cancelBooking(bookingId: String, reason: String): Resource<Unit> =
        runCatching {
            client.postgrest[Tables.BOOKINGS].update(
                // Save the cancellation reason in the dedicated `cancel_reason`
                // column so the customer's original note (containing phone,
                // name, address details) is not overwritten.
                buildJsonObject {
                    put("status", "cancelled")
                    put("cancel_reason", reason)
                    put("updated_at", java.time.Instant.now().toString())
                }
            ) { filter { eq("id", bookingId) } }
            Resource.Success(Unit)
        }.getOrElse { Resource.Error(it.message ?: "Hủy thất bại") }

    override suspend fun confirmCompletion(bookingId: String): Resource<Booking> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject {
                    put("status", "completed")
                    put("updated_at", java.time.Instant.now().toString())
                }
            ) {
                filter { eq("id", bookingId) }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Xác nhận hoàn thành thất bại") }

    override suspend fun rejectCompletion(bookingId: String, reason: String): Resource<Booking> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS].update(
                // Reject reason goes into worker_note (the worker is the one being
                // told to redo the work). customer_note must stay untouched.
                buildJsonObject {
                    put("status", "in_progress")
                    put("worker_note", reason)
                    put("updated_at", java.time.Instant.now().toString())
                }
            ) {
                filter { eq("id", bookingId) }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Từ chối hoàn thành thất bại") }

    override suspend fun getBookingById(bookingId: String): Resource<Booking> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS]
                .select(Columns.ALL) { filter { eq("id", bookingId) } }
                .decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Không tìm thấy booking") }

    override suspend fun updateBookingStatus(bookingId: String, status: String): Resource<Booking> =
        runCatching {
            val result = client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject {
                    put("status", status)
                    put("updated_at", java.time.Instant.now().toString())
                }
            ) {
                filter { eq("id", bookingId) }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Cập nhật trạng thái thất bại") }

    override fun observeBooking(bookingId: String): Flow<Booking?> {
        val channel = client.realtime.channel("booking_updates_$bookingId")

        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = Tables.BOOKINGS
            filter("id", FilterOperator.EQ, bookingId)
        }.map { action ->
            runCatching { action.decodeRecord<BookingDto>().toDomain() }.getOrNull()
        }
        return channel.liveFlow(changes)
    }

    override suspend fun deleteBooking(bookingId: String): Resource<Unit> =
        runCatching {
            // Delete associated table rows first to prevent foreign key issues
            runCatching {
                client.postgrest[Tables.BIDS].delete {
                    filter { eq("booking_id", bookingId) }
                }
            }
            runCatching {
                client.postgrest[Tables.PAYMENTS].delete {
                    filter { eq("booking_id", bookingId) }
                }
            }
            runCatching {
                client.postgrest[Tables.REVIEWS].delete {
                    filter { eq("booking_id", bookingId) }
                }
            }
            client.postgrest[Tables.BOOKINGS].delete {
                filter { eq("id", bookingId) }
            }
            Resource.Success(Unit)
        }.getOrElse { Resource.Error(it.message ?: "Xóa booking thất bại") }

    override suspend fun updateBooking(booking: Booking): Resource<Booking> =
        runCatching {
            val dto = booking.toDto().copy(updatedAt = java.time.Instant.now().toString())
            val result = client.postgrest[Tables.BOOKINGS].update(dto) {
                filter { eq("id", booking.id) }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Cập nhật booking thất bại") }

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