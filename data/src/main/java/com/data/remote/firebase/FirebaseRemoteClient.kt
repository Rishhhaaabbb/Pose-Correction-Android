package com.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRemoteClient @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    // ΓöÇΓöÇ Collections ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    private val profiles get() = firestore.collection("profiles")
    private val sessions get() = firestore.collection("workout_sessions")
    private val sessionMetrics get() = firestore.collection("session_metrics")
    private val poseGraphs get() = firestore.collection("pose_graphs")
    private val dailyStats get() = firestore.collection("daily_stats")
    private val onboardingResponses get() = firestore.collection("onboarding_responses")

    // ΓöÇΓöÇ Profiles ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    suspend fun fetchProfile(userId: String): FirestoreProfileDto? {
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
        profiles.document(dto.id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
    }

    // ΓöÇΓöÇ Workout Sessions ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    suspend fun insertSession(dto: FirestoreSessionDto) {
        sessions.document(dto.id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
    }

    // ΓöÇΓöÇ Session Metrics ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    suspend fun upsertSessionMetric(dto: FirestoreSessionMetricDto) {
        sessionMetrics.document(dto.id)
            .set(dto.toMap(), SetOptions.merge())
            .await()
    }

    // ΓöÇΓöÇ Pose Graphs ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    suspend fun upsertPoseGraph(dto: FirestorePoseGraphDto) {
        poseGraphs.document(dto.id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
    }

    // ΓöÇΓöÇ Daily Stats ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    suspend fun upsertDailyStat(dto: FirestoreDailyStatDto) {
        dailyStats.document(dto.id)
            .set(dto.toMap(), SetOptions.merge())
            .await()
    }

    // ΓöÇΓöÇ Onboarding Responses ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    suspend fun upsertOnboardingResponse(dto: FirestoreOnboardingDto) {
        onboardingResponses.document(dto.user_id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
    }

    suspend fun fetchOnboardingResponse(userId: String): FirestoreOnboardingDto? {
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
}