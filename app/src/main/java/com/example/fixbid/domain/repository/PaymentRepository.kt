package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentMethod
import com.example.fixbid.domain.model.Resource

interface PaymentRepository {
    suspend fun createPayment(bookingId: String, amount: Double, method: PaymentMethod): Resource<Payment>

    suspend fun confirmCashPayment(bookingId: String): Resource<Payment>
    suspend fun getPaymentByBooking(bookingId: String): Resource<Payment>
    suspend fun getPaymentHistory(userId: String): Resource<List<Payment>>

    suspend fun updatePaymentToEscrow(paymentId: String, transactionId: String): Resource<Payment>
    suspend fun releaseEscrow(bookingId: String): Resource<Payment>
    suspend fun refundPayment(bookingId: String, reason: String): Resource<Payment>
}