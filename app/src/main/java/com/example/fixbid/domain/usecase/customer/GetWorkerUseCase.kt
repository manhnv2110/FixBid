package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.WorkerRepository
import javax.inject.Inject

class GetWorkersUseCase @Inject constructor(
    private val workerRepository: WorkerRepository
) {
    suspend operator fun invoke(
        category: ServiceCategory? = null,
        query: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Double? = null,
        minRating: Double? = null,
        maxPricePerHour: Double? = null,
        page: Int = 0
    ): Resource<List<WorkerProfile>> = workerRepository.getWorkers(
        category, query, latitude, longitude, radiusKm, minRating, maxPricePerHour, page
    )
}