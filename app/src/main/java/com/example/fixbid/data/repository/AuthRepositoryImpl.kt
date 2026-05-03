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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : AuthRepository {

    override val currentUser: Flow<User?> = client.auth.sessionStatus.map { status ->
        runCatching {
            val session = client.auth.currentSessionOrNull() ?: return@map null
            client.postgrest[Tables.PROFILES]
                .select(Columns.ALL) { filter { eq("id", session.user!!.id) } }
                .decodeSingle<UserDto>()
                .toDomain()
        }.getOrNull()
    }

    override suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phoneNumber: String,
        role: UserRole
    ): Resource<User> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val userId = client.auth.currentSessionOrNull()!!.user!!.id

        // Tạo profile trong bảng profiles
        val dto = UserDto(
            id = userId,
            email = email,
            fullName = fullName,
            phoneNumber = phoneNumber,
            role = role.name.lowercase()
        )
        client.postgrest[Tables.PROFILES].insert(dto)

        // Nếu là thợ, tạo worker_profile rỗng
        if (role == UserRole.WORKER) {
            client.postgrest[Tables.WORKER_PROFILES].insert(
                mapOf("user_id" to userId)
            )
        }

        Resource.Success(dto.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Đăng ký thất bại") }

    override suspend fun signIn(email: String, password: String): Resource<User> =
        runCatching {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.auth.currentSessionOrNull()!!.user!!.id
            val user = client.postgrest[Tables.PROFILES]
                .select(Columns.ALL) { filter { eq("id", userId) } }
                .decodeSingle<UserDto>()
                .toDomain()
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
        val userId = client.auth.currentSessionOrNull()?.user?.id ?: return null
        client.postgrest[Tables.PROFILES]
            .select(Columns.ALL) { filter { eq("id", userId) } }
            .decodeSingle<UserDto>()
            .toDomain()
    }.getOrNull()

    override suspend fun updateProfile(user: User): Resource<User> = runCatching {
        client.postgrest[Tables.PROFILES]
            .update(user.toDto()) { filter { eq("id", user.id) } }
        Resource.Success(user)
    }.getOrElse { Resource.Error(it.message ?: "Cập nhật thất bại") }
}