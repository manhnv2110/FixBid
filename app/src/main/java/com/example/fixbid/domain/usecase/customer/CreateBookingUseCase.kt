package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingType
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BookingRepository
import javax.inject.Inject

class CreateBookingUseCase @Inject constructor(
    private val bookingRepository: BookingRepository
) {
    suspend operator fun invoke(booking: Booking): Resource<Booking> {
        return when (booking.type) {
            BookingType.DIRECT  -> bookingRepository.createDirectBooking(booking)
            BookingType.BIDDING -> bookingRepository.createBiddingBooking(booking)
        }
    }
}