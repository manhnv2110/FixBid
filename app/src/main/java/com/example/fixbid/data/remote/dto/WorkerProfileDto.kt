package com.example.fixbid.data.remote.dto

import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.model.WorkerProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkerProfileDto(
    @SerialName("user_id") val userId: String = "",
    val bio: String = "",
    val skills: List<String> = emptyList(),
    @SerialName("experience_years") val experienceYears: Int = 0,
    @SerialName("price_per_hour") val pricePerHour: Double = 0.0,
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("average_rating") val averageRating: Double = 0.0,
    @SerialName("total_reviews") val totalReviews: Int = 0,
    @SerialName("total_jobs_done") val totalJobsDone: Int = 0,
    @SerialName("identity_verified") val identityVerified: Boolean = false
) {
    fun toDomain() = WorkerProfile(
        userId = userId,
        bio = bio,
        skills = skills.mapNotNull { runCatching { ServiceCategory.valueOf(it) }.getOrNull() },
        experienceYears = experienceYears,
        pricePerHour = pricePerHour,
        location = location,
        latitude = latitude,
        longitude = longitude,
        isAvailable = isAvailable,
        averageRating = averageRating,
        totalReviews = totalReviews,
        totalJobsDone = totalJobsDone,
        identityVerified = identityVerified
    )
}