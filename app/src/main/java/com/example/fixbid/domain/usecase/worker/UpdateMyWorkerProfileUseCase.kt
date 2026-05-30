package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.WorkerRepository
import javax.inject.Inject

/**
 * Validates and saves the signed-in worker's professional profile. Creates the
 * `worker_profiles` row on first save (repository upsert) so a worker who never
 * had one can still set up their info.
 */
class UpdateMyWorkerProfileUseCase @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        bio: String,
        skills: List<ServiceCategory>,
        experienceYears: Int,
        pricePerHour: Double,
        location: String,
        isAvailable: Boolean,
        // preserve existing server-managed values for the returned domain object
        existing: WorkerProfile?
    ): Resource<WorkerProfile> {
        if (skills.isEmpty()) return Resource.Error("Vui lòng chọn ít nhất 1 kỹ năng")
        if (experienceYears < 0) return Resource.Error("Số năm kinh nghiệm không hợp lệ")
        if (pricePerHour < 0) return Resource.Error("Giá theo giờ không hợp lệ")

        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")

        val profile = WorkerProfile(
            userId = user.id,
            bio = bio.trim(),
            skills = skills,
            experienceYears = experienceYears,
            pricePerHour = pricePerHour,
            location = location.trim(),
            latitude = existing?.latitude,
            longitude = existing?.longitude,
            isAvailable = existing?.isAvailable ?: isAvailable,
            averageRating = existing?.averageRating ?: 0.0,
            totalReviews = existing?.totalReviews ?: 0,
            totalJobsDone = existing?.totalJobsDone ?: 0,
            identityVerified = existing?.identityVerified ?: false
        )
        return workerRepository.updateWorkerProfile(profile)
    }
}
