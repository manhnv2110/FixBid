package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.UserDto
import com.example.fixbid.data.remote.dto.toDto
import com.example.fixbid.data.remote.dto.toUpdateDto
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
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : AuthRepository {

    @Volatile
    private var cachedUser: User? = null

    override val currentUser: Flow<User?> =
        client.auth.sessionStatus.map { status ->
            if (status is SessionStatus.Authenticated) {
                val user = fetchUserProfile()
                cachedUser = user
                user
            } else {
                cachedUser = null
                null
            }
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
        val user = fetchUserProfile() ?: return Resource.Error("Không thể tạo tài khoản")
        cachedUser = user
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
        val user = fetchUserProfile() ?: return Resource.Error("Đăng nhập thất bại")
        cachedUser = user
        Resource.Success(user)
    }.getOrElse { Resource.Error(it.message ?: "Đăng nhập thất bại") }

    override suspend fun signOut(): Resource<Unit> = runCatching {
        client.auth.signOut()
        cachedUser = null
        Resource.Success(Unit)
    }.getOrElse { Resource.Error(it.message ?: "Lỗi đăng xuất") }

    override suspend fun resetPassword(email: String): Resource<Unit> = runCatching {
        client.auth.resetPasswordForEmail(email)
        Resource.Success(Unit)
    }.getOrElse { Resource.Error(it.message ?: "Lỗi") }

    override suspend fun getCurrentUser(): User? {
        awaitSession()
        val cached = cachedUser
        if (cached != null) return cached
        val user = fetchUserProfile()
        cachedUser = user
        return user
    }

    override suspend fun updateProfile(user: User): Resource<User> = runCatching {
        awaitSession()
        client.from(Tables.PROFILES)
            .update(user.toUpdateDto()) { filter { eq("id", user.id) } }
        cachedUser = user
        Resource.Success(user)
    }.getOrElse { Resource.Error(it.message ?: "Cập nhật thất bại") }

    override suspend fun uploadAvatar(
        userId: String,
        imageBytes: ByteArray,
        fileName: String
    ): Resource<String> = runCatching {
        awaitSession()
        val path = "$userId/$fileName"
        val bucket = client.storage.from("avatars")
        bucket.upload(path, imageBytes) { upsert = true }
        val publicUrl = bucket.publicUrl(path)
        val cacheBustedUrl = "$publicUrl?t=${System.currentTimeMillis()}"
        client.from(Tables.PROFILES)
            .update(buildJsonObject { put("avatar_url", cacheBustedUrl) }) {
                filter { eq("id", userId) }
            }
        cachedUser = cachedUser?.copy(avatarUrl = cacheBustedUrl)
        Resource.Success(cacheBustedUrl)
    }.getOrElse { Resource.Error(it.message ?: "Upload ảnh thất bại") }

    /**
     * Đợi cho đến khi session status không còn Initializing.
     * Đảm bảo auth token đã sẵn sàng trước khi gọi postgrest.
     */
    private suspend fun awaitSession() {
        client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
    }

    /**
     * Fetch profile từ DB. Chỉ gọi khi đã chắc chắn có session.
     */
    private suspend fun fetchUserProfile(): User? = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: return null
        client.from(Tables.PROFILES)
            .select { filter { eq("id", userId) } }
            .decodeSingle<UserDto>()
            .toDomain()
    }.getOrNull()
}
