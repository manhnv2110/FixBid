package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.Resource
import kotlinx.coroutines.flow.Flow

interface BidRepository {
    suspend fun placeBid(bid: Bid): Resource<Bid>
    suspend fun getBidsForBooking(bookingId: String): Resource<List<Bid>>
    suspend fun getMyBids(workerId: String): Resource<List<Bid>>
    suspend fun acceptBid(bidId: String): Resource<Bid>
    suspend fun rejectBid(bidId: String): Resource<Unit>
    suspend fun withdrawBid(bidId: String): Resource<Unit>
    fun observeBidsForBooking(bookingId: String): Flow<List<Bid>>
}