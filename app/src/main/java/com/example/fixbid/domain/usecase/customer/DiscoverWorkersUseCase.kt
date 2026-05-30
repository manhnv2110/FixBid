package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.data.repository.ProfileRepository
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.WorkerRepository
import javax.inject.Inject

/** A worker profile paired with their display name/avatar for list rendering. */
data class DiscoveredWorker(
    val profile: WorkerProfile,
    val displayName: String,
    val avatarUrl: String?
)

enum class WorkerSortBy(val label: String) {
    RATING("Đánh giá cao"),
    PRICE_LOW("Giá thấp"),
    PRICE_HIGH("Giá cao"),
    EXPERIENCE("Kinh nghiệm")
}

/**
 * Loads available workers for the customer discovery screen, optionally filtered
 * by category, then enriches each with their display name/avatar from `profiles`
 * (deduped) and applies client-side sorting.
 */
class DiscoverWorkersUseCase @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(
        category: ServiceCategory? = null,
        sortBy: WorkerSortBy = WorkerSortBy.RATING
    ): Resource<List<DiscoveredWorker>> {
        val result = workerRepository.getWorkers(
            category = category,
            page = 0,
            pageSize = 50
        )
        if (result !is Resource.Success) {
            return Resource.Error((result as? Resource.Error)?.message ?: "Lỗi tải danh sách thợ")
        }

        val sorted = when (sortBy) {
            WorkerSortBy.RATING -> result.data.sortedByDescending { it.averageRating }
            WorkerSortBy.PRICE_LOW -> result.data.sortedBy { it.pricePerHour }
            WorkerSortBy.PRICE_HIGH -> result.data.sortedByDescending { it.pricePerHour }
            WorkerSortBy.EXPERIENCE -> result.data.sortedByDescending { it.experienceYears }
        }

        val enriched = sorted.map { profile ->
            val account = profileRepository.getProfile(profile.userId).getOrNull()
            DiscoveredWorker(
                profile = profile,
                displayName = account?.fullName ?: "Thợ dịch vụ",
                avatarUrl = account?.avatarUrl
            )
        }
        return Resource.Success(enriched)
    }
}
