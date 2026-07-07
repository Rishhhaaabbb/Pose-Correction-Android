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
            android.util.Log.d("AuthRepository", "Restoring dummy session for: ${dummySession.email}")
            
            // If this is admin, also sign into Firebase anonymously
            if (dummySession.email == "admin@poseapp.com" || dummySession.email == "admin") {
                android.util.Log.d("AuthRepository", "Admin dummy session detected, signing into Firebase...")
                try {
                    if (firebaseAuth.currentUser == null) {
                        val result = firebaseAuth.signInAnonymously().await()
                        android.util.Log.d("AuthRepository", "✓ Signed into Firebase anonymously: uid=${result.user?.uid}")
                    } else {
                        android.util.Log.d("AuthRepository", "Already signed into Firebase: uid=${firebaseAuth.currentUser?.uid}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AuthRepository", "✗ Failed to sign into Firebase: ${e.message}", e)
                }
            }
            
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
        val normalizedEmail = email.trim().lowercase().replace(" ", "")
        val normalizedPassword = password.trim()
        
        // Special handling for admin - sign into Firebase anonymously to enable Firestore access
        if (normalizedEmail == "admin" || normalizedEmail == "admin@poseapp.com") {
            if (normalizedPassword == "admin") {
                android.util.Log.d("AuthRepository", "Admin login detected, attempting Firebase anonymous sign-in...")
                
                // Sign in anonymously to Firebase so Firestore rules work
                try {
                    val result = firebaseAuth.signInAnonymously().await()
                    android.util.Log.d("AuthRepository", "✓ Admin signed into Firebase anonymously: uid=${result.user?.uid}")
                } catch (e: Exception) {
                    android.util.Log.e("AuthRepository", "✗ Failed to sign into Firebase anonymously: ${e.message}", e)
                    // Continue anyway - admin will work locally but not have Firestore access
                }
                
                val adminUser = AuthUser(id = "admin-dev-id", email = "admin@poseapp.com", fullName = "Admin")
                setDummySession(adminUser)
                
                // Verify Firebase auth state
                val currentUser = firebaseAuth.currentUser
                android.util.Log.d("AuthRepository", "Firebase currentUser after sign-in: ${currentUser?.uid}, isAnonymous: ${currentUser?.isAnonymous}")
                
                return@runCatching adminUser
            }
        }
        
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
