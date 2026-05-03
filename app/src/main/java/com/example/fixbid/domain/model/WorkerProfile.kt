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

enum class ServiceCategory(val displayName: String) {
    PLUMBING("Sửa ống nước"),
    ELECTRICAL("Sửa điện"),
    CARPENTRY("Mộc / Nội thất"),
    PAINTING("Sơn nhà"),
    AIR_CONDITIONING("Điều hòa"),
    APPLIANCE_REPAIR("Sửa đồ gia dụng"),
    CLEANING("Vệ sinh"),
    LOCKSMITH("Khóa cửa"),
    ROOFING("Mái nhà"),
    OTHER("Khác")
}