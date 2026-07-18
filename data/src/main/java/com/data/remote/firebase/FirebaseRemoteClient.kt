package com.data.remote.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a write is attempted without a signed-in Firebase user. */
class UnauthenticatedException(message: String = "No authenticated Firebase user") : Exception(message)

@Singleton
class FirebaseRemoteClient @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    // ── Collections ────────────────────────────────────────────────────────
    private val profiles get() = firestore.collection("profiles")
    private val sessions get() = firestore.collection("workout_sessions")
    private val sessionMetrics get() = firestore.collection("session_metrics")
    private val poseGraphs get() = firestore.collection("pose_graphs")
    private val dailyStats get() = firestore.collection("daily_stats")
    private val onboardingResponses get() = firestore.collection("onboarding_responses")

    // ── Auth guard ────────────────────────────────────────────────────────
    private fun requireFirebaseAuth() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            throw UnauthenticatedException()
        }
    }

    // ── Profiles ──────────────────────────────────────────────────────────

    suspend fun fetchProfile(userId: String): FirestoreProfileDto? {
        Log.d(TAG, "fetchProfile: userId=$userId")
        val snapshot = profiles.document(userId).get().await()
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        return FirestoreProfileDto(
            id = data["id"] as? String ?: userId,
            display_name = data["display_name"] as? String ?: "",
            email = data["email"] as? String,
            height_cm = (data["height_cm"] as? Long)?.toInt(),
            weight_kg = data["weight_kg"] as? Double,
            fitness_goal = data["fitness_goal"] as? String,
            target_weight_kg = data["target_weight_kg"] as? Double,
            weekly_goal_sessions = (data["weekly_goal_sessions"] as? Long)?.toInt(),
            updated_at = data["updated_at"] as? Long ?: 0L,
        )
    }

    suspend fun upsertProfile(dto: FirestoreProfileDto) {
        requireFirebaseAuth()
        Log.d(TAG, "upsertProfile: id=${dto.id}")
        profiles.document(dto.id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
        Log.i(TAG, "upsertProfile: succeeded — id=${dto.id}")
    }

    /** Patch only specific fields in the profile document without touching others. */
    suspend fun patchProfileFields(userId: String, fields: Map<String, Any?>) {
        requireFirebaseAuth()
        val cleaned = fields.filterValues { it != null }
        if (cleaned.isEmpty()) return
        Log.d(TAG, "patchProfileFields: userId=$userId, fields=${cleaned.keys}")
        profiles.document(userId)
            .set(cleaned, SetOptions.merge())
            .await()
        Log.i(TAG, "patchProfileFields: succeeded — userId=$userId")
    }

    // ── Workout Sessions ──────────────────────────────────────────────────

    suspend fun insertSession(dto: FirestoreSessionDto) {
        requireFirebaseAuth()
        Log.d(TAG, "insertSession: id=${dto.id}")
        sessions.document(dto.id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
        Log.i(TAG, "insertSession: succeeded — id=${dto.id}")
    }

    suspend fun deleteSession(sessionId: String) {
        requireFirebaseAuth()
        Log.d(TAG, "deleteSession: id=${sessionId}")
        sessions.document(sessionId).delete().await()
        Log.i(TAG, "deleteSession: succeeded — id=${sessionId}")
    }

    // ── Session Metrics ───────────────────────────────────────────────────

    suspend fun upsertSessionMetric(dto: FirestoreSessionMetricDto) {
        requireFirebaseAuth()
        Log.d(TAG, "upsertSessionMetric: id=${dto.id}")
        sessionMetrics.document(dto.id)
            .set(dto.toMap(), SetOptions.merge())
            .await()
        Log.i(TAG, "upsertSessionMetric: succeeded — id=${dto.id}")
    }

    // ── Pose Graphs ───────────────────────────────────────────────────────

    suspend fun upsertPoseGraph(dto: FirestorePoseGraphDto) {
        requireFirebaseAuth()
        Log.d(TAG, "upsertPoseGraph: id=${dto.id}")
        poseGraphs.document(dto.id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
        Log.i(TAG, "upsertPoseGraph: succeeded — id=${dto.id}")
    }

    // ── Daily Stats ───────────────────────────────────────────────────────

    suspend fun upsertDailyStat(dto: FirestoreDailyStatDto) {
        requireFirebaseAuth()
        Log.d(TAG, "upsertDailyStat: id=${dto.id}")
        dailyStats.document(dto.id)
            .set(dto.toMap(), SetOptions.merge())
            .await()
        Log.i(TAG, "upsertDailyStat: succeeded — id=${dto.id}")
    }

    // ── Onboarding Responses ──────────────────────────────────────────────

    suspend fun upsertOnboardingResponse(dto: FirestoreOnboardingDto) {
        requireFirebaseAuth()
        Log.d(TAG, "upsertOnboardingResponse: userId=${dto.user_id}")
        onboardingResponses.document(dto.user_id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
        Log.i(TAG, "upsertOnboardingResponse: succeeded — userId=${dto.user_id}")
    }

    suspend fun fetchOnboardingResponse(userId: String): FirestoreOnboardingDto? {
        Log.d(TAG, "fetchOnboardingResponse: userId=$userId")
        val snapshot = onboardingResponses.document(userId).get().await()
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        return FirestoreOnboardingDto(
            user_id = data["user_id"] as? String ?: userId,
            age_group = data["age_group"] as? String,
            gender = data["gender"] as? String,
            goal = data["goal"] as? String,
            experience_level = data["experience_level"] as? String,
            yoga_style = data["yoga_style"] as? String,
            flexibility_level = data["flexibility_level"] as? String,
            focus_areas = data["focus_areas"] as? String,
            height_cm = (data["height_cm"] as? Long)?.toInt(),
            weight_kg = data["weight_kg"] as? Double,
            daily_practice_minutes = (data["daily_practice_minutes"] as? Long)?.toInt(),
            weekly_frequency = data["weekly_frequency"] as? String,
            injuries = data["injuries"] as? String,
            medical_conditions = data["medical_conditions"] as? String,
            camera_permission_granted = data["camera_permission_granted"] as? Boolean,
            is_completed = (data["is_completed"] as? Boolean) ?: false,
            updated_at = (data["updated_at"] as? Long) ?: 0L,
        )
    }

    companion object {
        private const val TAG = "FirebaseRemoteClient"
    }
}
