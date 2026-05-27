package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BidRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.WorkerRepository
import javax.inject.Inject

/**
 * Lấy danh sách job đấu thầu (BIDDING) đang mở mà worker hiện tại có thể đặt giá.
 *
 * - Mặc định lọc theo skills của worker (nếu có).
 * - Loại bỏ những booking mà worker đã từng đặt thầu (PENDING/ACCEPTED).
 */
class GetOpenJobRequestsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val bidRepository: BidRepository,
    private val workerRepository: WorkerRepository
) {
    suspend operator fun invoke(
        categoryFilter: ServiceCategory? = null,
        applySkillsFilter: Boolean = true
    ): Resource<List<Booking>> {
        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")

        val skills: List<ServiceCategory>? = if (applySkillsFilter) {
            (workerRepository.getWorkerById(user.id) as? Resource.Success)
                ?.data?.skills?.takeIf { it.isNotEmpty() }
        } else null

        val effectiveCategories = when {
            categoryFilter != null -> listOf(categoryFilter)
            skills != null -> skills
            else -> null
        }

        val myBids = (bidRepository.getMyBids(user.id) as? Resource.Success)
            ?.data ?: emptyList()
        val excludeBookingIds = myBids.map { it.bookingId }

        return bookingRepository.getOpenJobRequests(
            categories = effectiveCategories,
            excludeBookingIds = excludeBookingIds
        )
    }
}
