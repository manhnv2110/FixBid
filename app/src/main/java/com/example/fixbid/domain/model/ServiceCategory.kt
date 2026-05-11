package com.example.fixbid.domain.model
enum class ServiceCategory(
    val displayName: String,
    val iconRes: Int = 0   // sẽ được map ở tầng UI, tạm để 0
) {
    PLUMBING("Sửa ống nước"),
    ELECTRICAL("Sửa điện"),
    CARPENTRY("Mộc / Nội thất"),
    AIR_CONDITIONING("Điều hòa"),
    APPLIANCE_REPAIR("Sửa đồ gia dụng"),
    CLEANING("Vệ sinh"),
    LOCKSMITH("Khóa cửa"),
    ROOFING("Mái nhà"),
    OTHER("Khác")
}