package com.example.fixbid.domain.model
enum class ServiceCategory(
    val displayName: String,
    val iconRes: Int = 0   // sẽ được map ở tầng UI, tạm để 0
) {
    PLUMBING("Ống nước"),
    ELECTRICAL("Điện"),
    CARPENTRY("Mộc / Nội thất"),
    AIR_CONDITIONING("Điều hòa"),
    APPLIANCE_REPAIR("Đồ gia dụng"),
    CLEANING("Vệ sinh"),
    LOCKSMITH("Khóa cửa"),
    ROOFING("Mái nhà"),
    OTHER("Khác")
}