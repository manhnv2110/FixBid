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

        // Lấy workerId từ booking (dùng BookingDto vì bảng có nhiều kiểu dữ liệu khác nhau)
        val bookingDto = client.from(Tables.BOOKINGS)
            .select { filter { eq("id", bookingId) } }
            .decodeSingle<com.example.fixbid.data.remote.dto.BookingDto>()

        val workerId = bookingDto.workerId
            ?: return Resource.Error("Booking chưa có thợ")

        val platformFee = com.example.fixbid.core.utils.PaymentConstants.platformFee(amount)
        val workerReceives = amount - platformFee

        val result = client.from(Tables.PAYMENTS)
            .insert(buildJsonObject {
                put("booking_id", bookingId)
                put("customer_id", userId)
                put("worker_id", workerId)
                put("amount", amount)
                put("platform_fee", platformFee)
                put("worker_receives", workerReceives)
                put("method", method.name.lowercase())
                put("status", "pending")
                put("escrow_status", "none")
            }) { select() }
            .decodeSingle<PaymentDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Tạo thanh toán thất bại") }

    override suspend fun confirmCashPayment(bookingId: String): Resource<Payment> =
        runCatching {
            val result = client.from(Tables.PAYMENTS)
                .update(buildJsonObject {
                    put("status", "completed")
                    put("escrow_status", "none")
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
                            eq("worker_id", userId)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<PaymentDto>()
            Resource.Success(result.map { it.toDomain() })
        }.getOrElse { Resource.Error(it.message ?: "Lỗi tải lịch sử thanh toán") }

    /**
     * Cập nhật payment thành trạng thái ESCROW sau khi VNPay thanh toán thành công.
     * Tiền được hệ thống giữ cho đến khi job hoàn thành.
     */
    override suspend fun updatePaymentToEscrow(
        paymentId: String,
        transactionId: String
    ): Resource<Payment> = runCatching {
        val result = client.from(Tables.PAYMENTS)
            .update(buildJsonObject {
                put("status", "escrow")
                put("escrow_status", "holding")
                put("transaction_id", transactionId)
                put("paid_at", Instant.now().toString())
            }) {
                filter { eq("id", paymentId) }
                select()
            }
            .decodeSingle<PaymentDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Cập nhật escrow thất bại") }

    /**
     * Release tiền cho thợ sau khi khách xác nhận hoàn thành.
     */
    override suspend fun releaseEscrow(bookingId: String): Resource<Payment> =
        runCatching {
            val result = client.from(Tables.PAYMENTS)
                .update(buildJsonObject {
                    put("status", "completed")
                    put("escrow_status", "released")
                    put("released_at", Instant.now().toString())
                }) {
                    filter { eq("booking_id", bookingId) }
                    select()
                }
                .decodeSingle<PaymentDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Chuyển tiền cho thợ thất bại") }

    /**
     * Hoàn tiền cho khách (trường hợp tranh chấp).
     */
    override suspend fun refundPayment(bookingId: String, reason: String): Resource<Payment> =
        runCatching {
            val result = client.from(Tables.PAYMENTS)
                .update(buildJsonObject {
                    put("status", "refunded")
                    put("escrow_status", "refunded")
                }) {
                    filter { eq("booking_id", bookingId) }
                    select()
                }
                .decodeSingle<PaymentDto>()
            Resource.Success(result.toDomain())
        }.getOrElse { Resource.Error(it.message ?: "Hoàn tiền thất bại") }
}