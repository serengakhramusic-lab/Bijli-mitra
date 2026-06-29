package com.exmple.bijli_mitra.data.repository

import com.exmple.bijli_mitra.domain.model.User
import com.exmple.bijli_mitra.domain.model.UserRole
import com.exmple.bijli_mitra.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl : AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _currentUser = MutableStateFlow<User?>(null)

    init {
        // Initialize state if user is already logged in
        auth.currentUser?.let { firebaseUser ->
            firestore.collection("users").document(firebaseUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val roleStr = document.getString("role") ?: "PUBLIC"
                        val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.PUBLIC }
                        val user = User(
                            id = firebaseUser.uid,
                            name = document.getString("name") ?: "",
                            email = document.getString("email") ?: firebaseUser.email ?: "",
                            phone = document.getString("phone"),
                            role = role
                        )
                        _currentUser.value = user
                    }
                }
        }
    }

    override suspend fun checkAutoLogin(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return try {
            val document = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (document.exists()) {
                val roleStr = document.getString("role") ?: "PUBLIC"
                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.PUBLIC }
                val user = User(
                    id = firebaseUser.uid,
                    name = document.getString("name") ?: "",
                    email = document.getString("email") ?: firebaseUser.email ?: "",
                    phone = document.getString("phone"),
                    role = role
                )
                _currentUser.value = user
                user
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loginWithEmail(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("Authentication failed")
            
            val document = firestore.collection("users").document(uid).get().await()
            if (document.exists()) {
                val roleStr = document.getString("role") ?: "PUBLIC"
                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.PUBLIC }
                val user = User(
                    id = uid,
                    name = document.getString("name") ?: "",
                    email = document.getString("email") ?: email,
                    phone = document.getString("phone"),
                    role = role
                )
                _currentUser.value = user
                Result.success(user)
            } else {
                auth.signOut()
                Result.failure(Exception("Account not found. Please register first."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Account not found. Please register first."))
        }
    }

    override suspend fun loginWithPhone(phone: String, otp: String): Result<User> {
        // For simplicity and since we don't have an Activity context for real phone auth,
        // we check if the phone number exists in Firestore and validate with a mock OTP.
        return try {
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("phone", phone)
                .get()
                .await()
            
            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("Account not found. Please register first."))
            }
            
            val document = querySnapshot.documents.first()
            if (otp == "123456") {
                val roleStr = document.getString("role") ?: "PUBLIC"
                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.PUBLIC }
                val user = User(
                    id = document.id,
                    name = document.getString("name") ?: "",
                    email = document.getString("email") ?: "",
                    phone = document.getString("phone"),
                    role = role
                )
                _currentUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Invalid OTP"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Login failed: ${e.message}"))
        }
    }

    override suspend fun register(name: String, email: String, phone: String, password: String): Result<User> {
        return try {
            // Check if phone number is already registered (email is checked by Firebase Auth)
            if (phone.isNotBlank()) {
                val querySnapshot = firestore.collection("users")
                    .whereEqualTo("phone", phone)
                    .get()
                    .await()
                if (!querySnapshot.isEmpty) {
                    return Result.failure(Exception("Phone number is already registered."))
                }
            }

            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("Registration failed")
            
            val user = User(
                id = uid,
                name = name,
                email = email,
                phone = phone.ifBlank { null },
                role = UserRole.PUBLIC
            )
            
            val userData = hashMapOf(
                "name" to user.name,
                "email" to user.email,
                "phone" to user.phone,
                "role" to user.role.name
            )
            
            firestore.collection("users").document(uid).set(userData).await()
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception("Registration failed: ${e.message}"))
        }
    }

    override fun getCurrentUser(): Flow<User?> = _currentUser

    override suspend fun logout() {
        auth.signOut()
        _currentUser.value = null
    }
}
