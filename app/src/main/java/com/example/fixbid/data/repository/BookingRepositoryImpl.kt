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
            // Surface both PENDING (chưa báo giá) and QUOTED (đã báo giá, chờ
            // khách phản hồi) so the worker has a single inbox of direct
            // requests they're actively waiting on.
            val result = client.postgrest[Tables.BOOKINGS].select(Columns.ALL) {
                filter {
                    eq("worker_id", workerId)
                    eq("type", "direct")
                    filter("status", FilterOperator.IN, "(pending,quoted)")
                }
                order("created_at", Order.DESCENDING)
                limit(50)
            }.decodeList<BookingDto>()
            Resource.Success(result.map { it.toDomain() })
        }.getOrElse { Resource.Error(it.message ?: "Lỗi tải yêu cầu trực tiếp") }

    override suspend fun acceptDirectBooking(bookingId: String): Resource<Booking> =
        runCatching {
            // Legacy entry-point kept for backwards compatibility with any
            // call-site that still bypasses the quote step. We require an
            // explicit `agreed_price` (= the booking's existing quoted_price
            // if present, else the customer-supplied agreed_price) so the
            // payment screen can render. If neither is available we surface
            // an error rather than silently advancing to AWAITING_PAYMENT
            // with a null price (the original bug).
            val current = client.postgrest[Tables.BOOKINGS]
                .select(Columns.ALL) { filter { eq("id", bookingId) } }
                .decodeSingle<BookingDto>()
            val resolvedPrice = current.agreedPrice ?: current.quotedPrice
                ?: error("Không thể nhận đơn khi chưa có báo giá")
            val result = client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject {
                    put("status", "awaiting_payment")
                    put("agreed_price", resolvedPrice)
                    put("updated_at", java.time.Instant.now().toString())
                }
            ) {
                filter { eq("id", bookingId) }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Nhận đơn thất bại") }

    override suspend fun quoteDirectBooking(
        bookingId: String,
        proposedPrice: Double,
        message: String,
        estimatedDurationHours: Double?
    ): Resource<Booking> = runCatching {
        require(proposedPrice > 0) { "Giá báo phải lớn hơn 0" }
        val now = java.time.Instant.now().toString()
        val result = client.postgrest[Tables.BOOKINGS].update(
            buildJsonObject {
                put("status", "quoted")
                put("quoted_price", proposedPrice)
                put("quote_message", message)
                put("quoted_at", now)
                estimatedDurationHours?.takeIf { it > 0 }?.let {
                    put("quote_estimated_duration_hours", it)
                }
                // Clear any previous customer-rejection note so the worker
                // gets a clean slate when they re-quote after a rejection.
                put("worker_note", kotlinx.serialization.json.JsonNull)
                put("updated_at", now)
            }
        ) {
            // Only allow quoting on direct bookings that are still pending or
            // already quoted (re-quote after a rejection). Belt-and-braces:
            // the use case also gates this on the UI side.
            filter {
                eq("id", bookingId)
                eq("type", "direct")
                filter("status", FilterOperator.IN, "(pending,quoted)")
            }
            select(Columns.ALL)
        }.decodeSingle<BookingDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Gửi báo giá thất bại") }

    override suspend fun acceptDirectQuote(bookingId: String): Resource<Booking> =
        runCatching {
            // Read the current quoted_price first so we can copy it into
            // agreed_price atomically with the status flip. If quoted_price
            // is null we refuse to advance — that's the very bug this flow
            // is fixing.
            val current = client.postgrest[Tables.BOOKINGS]
                .select(Columns.ALL) { filter { eq("id", bookingId) } }
                .decodeSingle<BookingDto>()
            val price = current.quotedPrice
                ?: error("Đơn này chưa có báo giá để chấp nhận")
            require(current.status.equals("quoted", ignoreCase = true)) {
                "Đơn không ở trạng thái chờ duyệt báo giá"
            }
            val now = java.time.Instant.now().toString()
            val result = client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject {
                    put("status", "awaiting_payment")
                    put("agreed_price", price)
                    // Carry over the worker's quoted duration as the source
                    // of truth so the payment / completion flow uses it.
                    current.quoteEstimatedDurationHours?.let {
                        put("estimated_duration_hours", it)
                    }
                    put("updated_at", now)
                }
            ) {
                filter {
                    eq("id", bookingId)
                    eq("status", "quoted")
                }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Chấp nhận báo giá thất bại") }

    override suspend fun rejectDirectQuote(bookingId: String, reason: String?): Resource<Booking> =
        runCatching {
            val now = java.time.Instant.now().toString()
            val trimmedReason = reason?.trim()?.takeIf { it.isNotBlank() }
            val result = client.postgrest[Tables.BOOKINGS].update(
                buildJsonObject {
                    // Roll back to PENDING so the worker can either re-quote or
                    // decline. Clear the quote columns so the previous price
                    // doesn't leak into the next round.
                    put("status", "pending")
                    put("quoted_price", kotlinx.serialization.json.JsonNull)
                    put("quote_message", kotlinx.serialization.json.JsonNull)
                    put("quoted_at", kotlinx.serialization.json.JsonNull)
                    put("quote_estimated_duration_hours", kotlinx.serialization.json.JsonNull)
                    // Stash the rejection reason in worker_note so the worker
                    // sees actionable feedback when they reopen the request.
                    put("worker_note", trimmedReason ?: "")
                    put("updated_at", now)
                }
            ) {
                filter {
                    eq("id", bookingId)
                    eq("status", "quoted")
                }
                select(Columns.ALL)
            }.decodeSingle<BookingDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Từ chối báo giá thất bại") }

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