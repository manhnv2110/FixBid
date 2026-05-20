package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BookingRepository
import javax.inject.Inject

class ConfirmCompletionUseCase @Inject constructor(
    private val bookingRepository: BookingRepository
) {
    suspend fun confirm(bookingId: String): Resource<Booking> {
        return bookingRepository.confirmCompletion(bookingId)
    }

    suspend fun reject(bookingId: String, reason: String): Resource<Booking> {
        return bookingRepository.rejectCompletion(bookingId, reason)
    }
}