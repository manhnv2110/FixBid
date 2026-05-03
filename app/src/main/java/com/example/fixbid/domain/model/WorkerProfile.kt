package com.example.fixbid.domain.model

data class WorkerProfile(
    val userId: String,
    val bio: String,
    val skills: List<ServiceCategory>,
    val experienceYears: Int,
    val pricePerHour: Double,          // giá cơ bản theo giờ
    val location: String,
    val latitude: Double?,
    val longitude: Double?,
    val isAvailable: Boolean,
    val averageRating: Double,
    val totalReviews: Int,
    val totalJobsDone: Int,
    val identityVerified: Boolean
)