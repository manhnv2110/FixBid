package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BidRepository
import javax.inject.Inject

class PlaceBidUseCase @Inject constructor(
    private val bidRepository: BidRepository
) {
    suspend operator fun invoke(bid: Bid): Resource<Bid> {
        if (bid.proposedPrice <= 0)
            return Resource.Error("Giá đặt thầu phải lớn hơn 0")
        if (bid.message.isBlank())
            return Resource.Error("Vui lòng nhập lời giới thiệu")
        return bidRepository.placeBid(bid)
    }
}