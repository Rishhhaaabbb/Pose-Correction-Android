package com.domain.repository

import com.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeSession(): Flow<AuthUser?>

    suspend fun restoreSession(): Result<AuthUser?>

    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>

    suspend fun signUpWithEmail(name: String, email: String, password: String): Result<AuthUser>

    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>

    suspend fun sendPasswordReset(email: String): Result<Unit>

    suspend fun signOut(): Result<Unit>

    fun setDummySession(authUser: AuthUser)
}
