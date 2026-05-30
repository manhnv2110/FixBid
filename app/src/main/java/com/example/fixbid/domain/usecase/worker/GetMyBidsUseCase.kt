package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BidRepository
import com.example.fixbid.domain.repository.BookingRepository
import javax.inject.Inject

/** A bid paired with the booking it was placed on, for the "My bids" screen. */
data class MyBid(
    val bid: Bid,
    val booking: Booking?
)

/**
 * Loads the signed-in worker's bids, each enriched with its booking so the UI
 * can show what the bid was for, the current booking status, and route to the
 * job detail. Bookings are fetched once per unique id (deduped).
 */
class GetMyBidsUseCase @Inject constructor(
    private val bidRepository: BidRepository,
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Resource<List<MyBid>> {
        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")

        val bidsResult = bidRepository.getMyBids(user.id)
        if (bidsResult !is Resource.Success) {
            return Resource.Error((bidsResult as? Resource.Error)?.message ?: "Lỗi tải báo giá")
        }

        val bids = bidsResult.data
        val bookingCache = mutableMapOf<String, Booking?>()
        val enriched = bids.map { bid ->
            val booking = bookingCache.getOrPut(bid.bookingId) {
                (bookingRepository.getBookingById(bid.bookingId) as? Resource.Success)?.data
            }
            MyBid(bid = bid, booking = booking)
        }
        return Resource.Success(enriched)
    }

    suspend fun withdraw(bidId: String): Resource<Unit> = bidRepository.withdrawBid(bidId)
}
