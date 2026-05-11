package com.example.fixbid.data.repository

import com.example.fixbid.data.dto.ProfileDto
import com.example.fixbid.data.supabase
import com.example.fixbid.domain.model.UserRole
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

class ProfileRepository {

    @Serializable
    private data class PhoneLookupRow(val email: String?)

    /**
     * Upsert the profile row for the current authenticated user. Called right
     * after a successful sign-up OTP verification so the `profiles` row stays
     * in sync with the freshly-created `auth.users` record.
     */
    suspend fun upsertProfile(
        userId: String,
        email: String?,
        fullName: String,
        phoneNumber: String?,
        role: UserRole
    ): Result<ProfileDto> = runCatching {
        val payload = ProfileDto(
            id = userId,
            email = email,
            fullName = fullName,
            phoneNumber = phoneNumber,
            role = role,
            isActive = true
        )
        supabase.postgrest["profiles"]
            .upsert(payload) { select() }
            .decodeSingle<ProfileDto>()
    }

    suspend fun getProfile(userId: String): Result<ProfileDto> = runCatching {
        supabase.postgrest["profiles"]
            .select {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeSingle<ProfileDto>()
    }

    /**
     * Lookup email by phone number so the user can sign in using their phone
     * while the actual auth record is keyed by email. Requires an appropriate
     * RLS policy on `profiles` that allows anonymous read of `email` for a
     * given `phone_number`.
     */
    suspend fun findEmailByPhone(phoneNumber: String): Result<String?> = runCatching {
        supabase.postgrest["profiles"]
            .select(columns = Columns.list("email")) {
                filter { eq("phone_number", phoneNumber) }
                limit(1)
            }
            .decodeList<PhoneLookupRow>()
            .firstOrNull()
            ?.email
    }
}
