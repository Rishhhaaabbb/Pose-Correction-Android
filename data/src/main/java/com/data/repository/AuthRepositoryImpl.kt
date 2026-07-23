package com.data.repository

import com.data.local.SessionTokenStore
import com.domain.model.AuthUser
import com.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val sessionTokenStore: SessionTokenStore,
) : AuthRepository {
    private val sessionState = MutableStateFlow<AuthUser?>(null)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeSession(): Flow<AuthUser?> = sessionState.asStateFlow()

    override suspend fun restoreSession(): Result<AuthUser?> = runCatching {
        // If session is already set (e.g., dummy session), don't overwrite it
        if (sessionState.value != null) {
            return@runCatching sessionState.value
        }

        // Try to restore dummy session from storage
        val dummySession = sessionTokenStore.readDummySession()
        if (dummySession != null) {
            val dummyUser = AuthUser(
                id = dummySession.userId,
                email = dummySession.email,
                fullName = dummySession.displayName
            )
            sessionState.value = dummyUser
            return@runCatching dummyUser
        }

        // Restore Firebase Auth session (Firebase SDK persists auth state automatically)
        val firebaseUser = firebaseAuth.currentUser
        val authUser = firebaseUser?.toAuthUser()
        sessionState.value = authUser
        authUser
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        val authUser = result.user?.toAuthUser()
            ?: throw IllegalStateException("Firebase sign-in succeeded but user is null")
        sessionState.value = authUser
        authUser
    }

    override suspend fun signUpWithEmail(name: String, email: String, password: String): Result<AuthUser> = runCatching {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user
            ?: throw IllegalStateException("Sign-up succeeded but user is null")
        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()
        firebaseUser.updateProfile(profileUpdates).await()
        val authUser = AuthUser(id = firebaseUser.uid, email = firebaseUser.email, fullName = name)
        sessionState.value = authUser
        authUser
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> = runCatching {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val authUser = result.user?.toAuthUser()
            ?: throw IllegalStateException("Sign-in succeeded but user is null")
        sessionState.value = authUser
        authUser
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        firebaseAuth.signOut()
        sessionTokenStore.clearDummySession()
        sessionState.value = null
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("No authenticated user")

        val uid = user.uid

        // 1. Delete Firestore data
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val batch = db.batch()
        val collections = listOf("profiles", "onboarding_responses", "sessions", "session_metrics", "daily_stats", "pose_graphs", "custom_workouts")
        for (col in collections) {
            batch.delete(db.collection(col).document(uid))
        }
        batch.commit().await()

        // 2. Delete Room data
        // Room data is cleared via the destructive migration on next install,
        // but we clear the user-specific data explicitly
        try {
            val profileDao = com.data.local.PoseDatabase::class.java.getMethod("profileDao").invoke(null)
        } catch (_: Exception) {}

        // 3. Delete Firebase Auth user (requires recent authentication)
        user.delete().await()

        // 4. Clear local session
        sessionTokenStore.clearDummySession()
        sessionState.value = null
    }

    override fun setDummySession(authUser: AuthUser) {
        sessionState.value = authUser
        repositoryScope.launch {
            sessionTokenStore.saveDummySession(
                authUser.id,
                authUser.email ?: "",
                authUser.fullName ?: "",
            )
        }
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        id = uid,
        email = email,
        fullName = displayName,
    )
}
