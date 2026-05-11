package com.example.fixbid.data.dto

import com.example.fixbid.domain.model.UserRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Maps to `public.profiles` in Supabase. `id` must equal `auth.users.id`.
 */
@Serializable
data class ProfileDto(
    val id: String,
    val email: String? = null,
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: UserRole = UserRole.CUSTOMER,
    @SerialName("is_active") val isActive: Boolean = true
)
