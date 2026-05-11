package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BidRepository
import javax.inject.Inject

class AcceptBidUseCase @Inject constructor(
    private val bidRepository: BidRepository
) {
    suspend operator fun invoke(bidId: String): Resource<Bid> =
        bidRepository.acceptBid(bidId)
}