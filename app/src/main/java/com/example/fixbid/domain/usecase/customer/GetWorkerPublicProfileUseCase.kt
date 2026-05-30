package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.data.repository.ProfileRepository
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Review
import com.example.fixbid.domain.model.User
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.ReviewRepository
import com.example.fixbid.domain.repository.WorkerRepository
import javax.inject.Inject

/** Everything the customer-facing worker profile screen needs in one payload. */
data class WorkerPublicProfile(
    val workerId: String,
    val displayName: String,
    val avatarUrl: String?,
    val profile: WorkerProfile,
    val reviews: List<Review>
)

/**
 * Loads a worker's public profile for customers: professional details + the
 * worker's review history (each review enriched with the reviewer's display
 * name/avatar so the UI can show "who" left it).
 */
class GetWorkerPublicProfileUseCase @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val reviewRepository: ReviewRepository,
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(workerId: String): Resource<WorkerPublicProfile> {
        val profileResult = workerRepository.getWorkerById(workerId)
        if (profileResult !is Resource.Success) {
            return Resource.Error(
                (profileResult as? Resource.Error)?.message ?: "Không tải được hồ sơ thợ"
            )
        }

        // Worker's display name / avatar lives in `profiles`.
        val workerAccount = profileRepository.getProfile(workerId).getOrNull()
        val displayName = workerAccount?.fullName ?: "Thợ dịch vụ"
        val avatarUrl = workerAccount?.avatarUrl

        // Reviews (best-effort — an empty list is still a valid profile view).
        val reviews = (reviewRepository.getReviewsForWorker(workerId) as? Resource.Success)
            ?.data ?: emptyList()

        // Enrich each review with the reviewer's name/avatar (deduped lookups).
        val enriched = enrichWithCustomers(reviews)

        return Resource.Success(
            WorkerPublicProfile(
                workerId = workerId,
                displayName = displayName,
                avatarUrl = avatarUrl,
                profile = profileResult.data,
                reviews = enriched
            )
        )
    }

    private suspend fun enrichWithCustomers(reviews: List<Review>): List<Review> {
        if (reviews.isEmpty()) return reviews
        val cache = mutableMapOf<String, User?>()
        return reviews.map { review ->
            val customer = cache.getOrPut(review.customerId) {
                profileRepository.getProfile(review.customerId).getOrNull()?.let { dto ->
                    User(
                        id = dto.id,
                        email = dto.email ?: "",
                        fullName = dto.fullName,
                        phoneNumber = dto.phoneNumber,
                        avatarUrl = dto.avatarUrl,
                        role = UserRole.CUSTOMER,
                        createdAt = 0L
                    )
                }
            }
            review.copy(customer = customer)
        }
    }
}