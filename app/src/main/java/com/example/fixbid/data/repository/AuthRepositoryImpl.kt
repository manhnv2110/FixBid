package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.UserDto
import com.example.fixbid.data.remote.dto.toDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.User
import com.example.fixbid.domain.model.UserRole
import com.example.fixbid.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : AuthRepository {

    override val currentUser: Flow<User?> =
        client.auth.sessionStatus.map { status ->
            if (status is SessionStatus.Authenticated) {
                getCurrentUser()
            } else null
        }

    override suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phoneNumber: String,
        role: UserRole
    ): Resource<User> = runCatching {
        client.auth.signUpWith(Email) {
            this.email    = email
            this.password = password
            data = buildJsonObject {
                put("full_name",     fullName)
                put("phone_number",  phoneNumber)
                put("role",          role.name.lowercase())
            }
        }
        val user = getCurrentUser() ?: return Resource.Error("Không thể tạo tài khoản")
        Resource.Success(user)
    }.getOrElse { Resource.Error(it.message ?: "Đăng ký thất bại") }

    override suspend fun signIn(
        email: String,
        password: String
    ): Resource<User> = runCatching {
        client.auth.signInWith(Email) {
            this.email    = email
            this.password = password
        }
        val user = getCurrentUser() ?: return Resource.Error("Đăng nhập thất bại")
        Resource.Success(user)
    }.getOrElse { Resource.Error(it.message ?: "Đăng nhập thất bại") }

    override suspend fun signOut(): Resource<Unit> = runCatching {
        client.auth.signOut()
        Resource.Success(Unit)
    }.getOrElse { Resource.Error(it.message ?: "Lỗi đăng xuất") }

    override suspend fun resetPassword(email: String): Resource<Unit> = runCatching {
        client.auth.resetPasswordForEmail(email)
        Resource.Success(Unit)
    }.getOrElse { Resource.Error(it.message ?: "Lỗi") }

    override suspend fun getCurrentUser(): User? = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: return null
        client.from(Tables.PROFILES)
            .select { filter { eq("id", userId) } }
            .decodeSingle<UserDto>()
            .toDomain()
    }.getOrNull()

    override suspend fun updateProfile(user: User): Resource<User> = runCatching {
        client.from(Tables.PROFILES)
            .update(user.toDto()) { filter { eq("id", user.id) } }
        Resource.Success(user)
    }.getOrElse { Resource.Error(it.message ?: "Cập nhật thất bại") }
}