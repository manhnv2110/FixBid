package com.example.fixbid.domain.model

data class User(
    val id: String,
    val email: String,
    val fullName: String,
    val phoneNumber: String?,
    val avatarUrl: String?,
    val role: UserRole,
    val createdAt: Long
)