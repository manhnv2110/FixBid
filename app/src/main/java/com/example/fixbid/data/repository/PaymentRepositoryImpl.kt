package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.PaymentDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentMethod
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.PaymentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject

class PaymentRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : PaymentRepository {

    override suspend fun createPayment(
        bookingId: String,
        amount: Double,
        method: PaymentMethod
    ): Resource<Payment> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return Resource.Error("Chưa đăng nhập")

        // Lấy workerId từ booking
        val booking = client.from(Tables.BOOKINGS)
            .select { filter { eq("id", bookingId) } }
            .decodeSingle<Map<String, String?>>()

        val workerId = booking["worker_id"]
            ?: return Resource.Error("Booking chưa có thợ")

        val result = client.from(Tables.PAYMENTS)
            .insert(buildJsonObject {
                put("booking_id",  bookingId)
                put("customer_id", userId)
                put("worker_id",   workerId)
                put("amount",      amount)
                put("method",      method.name.lowercase())
                // platform_fee và worker_receives tự tính bởi trigger DB
            }) { select() }
            .decodeSingle<PaymentDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Tạo thanh toán thất bại") }

    override suspend fun confirmCashPayment(bookingId: String): Resource<Payment> =
        runCatching {
            val result = client.from(Tables.PAYMENTS)
                .update(buildJsonObject {
                    put("status",  "completed")
                    put("paid_at", Instant.now().toString())
                }) {
                    filter { eq("booking_id", bookingId) }
                    select()
                }
                .decodeSingle<PaymentDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Xác nhận thanh toán thất bại") }

    override suspend fun getPaymentByBooking(bookingId: String): Resource<Payment> =
        runCatching {
            val result = client.from(Tables.PAYMENTS)
                .select { filter { eq("booking_id", bookingId) } }
                .decodeSingle<PaymentDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Không tìm thấy thanh toán") }

    override suspend fun getPaymentHistory(userId: String): Resource<List<Payment>> =
        runCatching {
            val result = client.from(Tables.PAYMENTS)
                .select {
                    filter {
                        or {
                            eq("customer_id", userId)
                            eq("worker_id",   userId)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<PaymentDto>()
            Resource.Success(result.map { it.toDomain() })
        }.getOrElse { Resource.Error(it.message ?: "Lỗi tải lịch sử thanh toán") }
}