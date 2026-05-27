package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BidRepository
import com.example.fixbid.domain.repository.BookingRepository
import javax.inject.Inject

data class JobDetailData(
    val booking: Booking,
    val competitorBidsCount: Int,
    val lowestBid: Double?,
    val highestBid: Double?,
    val averageBid: Double?,
    val myBid: Bid?
)

class GetJobDetailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val bidRepository: BidRepository
) {
    suspend operator fun invoke(bookingId: String): Resource<JobDetailData> {
        val bookingResult = bookingRepository.getBookingById(bookingId)
        val booking = (bookingResult as? Resource.Success)?.data
            ?: return Resource.Error((bookingResult as? Resource.Error)?.message ?: "Không tìm thấy yêu cầu")

        val bids = (bidRepository.getBidsForBooking(bookingId) as? Resource.Success)
            ?.data ?: emptyList()

        val activeBids = bids.filter {
            it.status == com.example.fixbid.domain.model.BidStatus.PENDING ||
                    it.status == com.example.fixbid.domain.model.BidStatus.ACCEPTED
        }

        val currentUserId = authRepository.getCurrentUser()?.id
        val myBid = activeBids.firstOrNull { it.workerId == currentUserId }
        val competitors = activeBids.filter { it.workerId != currentUserId }

        return Resource.Success(
            JobDetailData(
                booking = booking,
                competitorBidsCount = competitors.size,
                lowestBid = competitors.minOfOrNull { it.proposedPrice },
                highestBid = competitors.maxOfOrNull { it.proposedPrice },
                averageBid = if (competitors.isEmpty()) null
                else competitors.sumOf { it.proposedPrice } / competitors.size,
                myBid = myBid
            )
        )
    }
}
