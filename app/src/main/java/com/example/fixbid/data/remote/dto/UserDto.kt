package com.example.fixbid.data.remote.dto

import com.example.fixbid.domain.model.User
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.core.utils.toEpochMillis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String = "",
    val email: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String = "customer",
    @SerialName("created_at") val createdAt: String = ""
) {
    fun toDomain() = User(
        id = id,
        email = email,
        fullName = fullName,
        phoneNumber = phoneNumber,
        avatarUrl = avatarUrl,
        role = if (role == "worker") UserRole.WORKER else UserRole.CUSTOMER,
        createdAt = createdAt.toEpochMillis()
    )
}

fun User.toDto() = UserDto(
    id = id,
    email = email,
    fullName = fullName,
    phoneNumber = phoneNumber,
    avatarUrl = avatarUrl,
    role = role.name.lowercase(),
    createdAt = createdAt.toString()
)