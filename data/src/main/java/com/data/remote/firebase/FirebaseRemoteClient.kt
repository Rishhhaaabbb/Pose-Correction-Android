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
    // ── Collections ────────────────────────────────────────────────────────────
    private val profiles get() = firestore.collection("profiles")
    private val sessions get() = firestore.collection("workout_sessions")
    private val sessionMetrics get() = firestore.collection("session_metrics")
    private val poseGraphs get() = firestore.collection("pose_graphs")
    private val dailyStats get() = firestore.collection("daily_stats")

    // ── Profiles ───────────────────────────────────────────────────────────────

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

    // ── Workout Sessions ───────────────────────────────────────────────────────

    suspend fun insertSession(dto: FirestoreSessionDto) {
        sessions.document(dto.id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
    }

    // ── Session Metrics ────────────────────────────────────────────────────────

    suspend fun upsertSessionMetric(dto: FirestoreSessionMetricDto) {
        sessionMetrics.document(dto.id)
            .set(dto.toMap(), SetOptions.merge())
            .await()
    }

    // ── Pose Graphs ────────────────────────────────────────────────────────────

    suspend fun upsertPoseGraph(dto: FirestorePoseGraphDto) {
        poseGraphs.document(dto.id)
            .set(dto.toMap().filterValues { it != null }, SetOptions.merge())
            .await()
    }

    // ── Daily Stats ────────────────────────────────────────────────────────────

    suspend fun upsertDailyStat(dto: FirestoreDailyStatDto) {
        dailyStats.document(dto.id)
            .set(dto.toMap(), SetOptions.merge())
            .await()
    }
}
