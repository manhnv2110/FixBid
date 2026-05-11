package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import javax.inject.Inject

class GetJobRequestsUseCase @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(status: BookingStatus? = null): Resource<List<Booking>> {
        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")
        return bookingRepository.getWorkerBookings(user.id, status)
    }
}