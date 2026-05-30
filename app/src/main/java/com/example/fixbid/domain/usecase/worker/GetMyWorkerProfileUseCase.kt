package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.WorkerRepository
import javax.inject.Inject

/** Loads the signed-in worker's own professional profile. */
class GetMyWorkerProfileUseCase @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Resource<WorkerProfile> {
        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")
        return workerRepository.getWorkerById(user.id)
    }
}
