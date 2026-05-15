package com.example.fixbid.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `user_role` enum defined in the Supabase database schema.
 */
@Serializable
enum class UserRole {
    @SerialName("customer")
    CUSTOMER,

    @SerialName("worker")
    WORKER;

    val displayName: String
        get() = when (this) {
            CUSTOMER -> "Khách hàng"
            WORKER -> "Thợ dịch vụ"
        }
}
