package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.model.WorkerProfile
import kotlinx.coroutines.flow.Flow

interface WorkerRepository {

    suspend fun getWorkers(
        category: ServiceCategory? = null,
        query: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Double? = null,
        minRating: Double? = null,
        maxPricePerHour: Double? = null,
        page: Int = 0,
        pageSize: Int = 20
    ): Resource<List<WorkerProfile>>

    suspend fun getWorkerById(workerId: String): Resource<WorkerProfile>
    suspend fun updateWorkerProfile(profile: WorkerProfile): Resource<WorkerProfile>
    suspend fun setAvailability(isAvailable: Boolean): Resource<Unit>
    fun observeWorkerProfile(workerId: String): Flow<WorkerProfile?>
}