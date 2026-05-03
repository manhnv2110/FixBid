package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BookingRepository
import javax.inject.Inject

class UpdateJobStatusUseCase @Inject constructor(
    private val bookingRepository: BookingRepository
) {
    suspend operator fun invoke(
        bookingId: String,
        newStatus: BookingStatus,
        workerNote: String? = null
    ): Resource<Booking> = when (newStatus) {
        BookingStatus.CONFIRMED    -> bookingRepository.confirmBooking(bookingId)
        BookingStatus.IN_PROGRESS  -> bookingRepository.startJob(bookingId)
        BookingStatus.COMPLETED    -> bookingRepository.completeJob(bookingId, workerNote)
        else -> Resource.Error("Trạng thái không hợp lệ")
    }
}