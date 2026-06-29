package com.exmple.bijli_mitra.domain.repository

import com.exmple.bijli_mitra.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun checkAutoLogin(): User?
    suspend fun loginWithEmail(email: String, password: String): Result<User>
    suspend fun loginWithPhone(phone: String, otp: String): Result<User>
    suspend fun register(name: String, email: String, phone: String, password: String): Result<User>
    fun getCurrentUser(): Flow<User?>
    suspend fun logout()
}
