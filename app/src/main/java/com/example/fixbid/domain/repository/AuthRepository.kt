package com.example.fixbid.domain.repository

import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.User
import com.example.fixbid.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>

    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phoneNumber: String,
        role: UserRole
    ): Resource<User>

    suspend fun signIn(email: String, password: String): Resource<User>
    suspend fun signOut(): Resource<Unit>
    suspend fun resetPassword(email: String): Resource<Unit>
    suspend fun getCurrentUser(): User?
    suspend fun updateProfile(user: User): Resource<User>
}