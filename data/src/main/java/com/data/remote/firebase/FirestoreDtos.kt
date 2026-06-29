package com.data.remote.firebase

import kotlinx.serialization.Serializable

@Serializable
data class FirestoreProfileDto(
    val id: String,
    val display_name: String,
    val profile_image_url: String? = null,
    val email: String? = null,
    val height_cm: Int? = null,
    val weight_kg: Double? = null,
    val fitness_goal: String? = null,
    val target_weight_kg: Double? = null,
    val weekly_goal_sessions: Int? = null,
    val updated_at: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "display_name" to display_name,
        "profile_image_url" to profile_image_url,
        "email" to email,
        "height_cm" to height_cm,
        "weight_kg" to weight_kg,
        "fitness_goal" to fitness_goal,
        "target_weight_kg" to target_weight_kg,
        "weekly_goal_sessions" to weekly_goal_sessions,
        "updated_at" to updated_at,
    )
}

@Serializable
data class FirestoreSessionDto(
    val id: String,
    val user_id: String,
    val exercise_name: String? = null,
    val target_reps: Int? = null,
    val total_reps: Int? = null,
    val accuracy_percent: Double? = null,
    val total_calories: Double? = null,
    val started_at: Long,
    val ended_at: Long? = null,
    val duration_sec: Int,
    val status: String,
    val updated_at: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "user_id" to user_id,
        "exercise_name" to exercise_name,
        "target_reps" to target_reps,
        "total_reps" to total_reps,
        "accuracy_percent" to accuracy_percent,
        "total_calories" to total_calories,
        "started_at" to started_at,
        "ended_at" to ended_at,
        "duration_sec" to duration_sec,
        "status" to status,
        "updated_at" to updated_at,
    )
}

@Serializable
data class FirestoreSessionMetricDto(
    val id: String,
    val session_id: String,
    val user_id: String,
    val metric_name: String,
    val metric_value: Double,
    val updated_at: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "session_id" to session_id,
        "user_id" to user_id,
        "metric_name" to metric_name,
        "metric_value" to metric_value,
        "updated_at" to updated_at,
    )
}

@Serializable
data class FirestorePoseGraphDto(
    val id: String,
    val user_id: String,
    val graph_name: String,
    val storage_path: String,
    val metadata_json: String? = null,
    val updated_at: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "user_id" to user_id,
        "graph_name" to graph_name,
        "storage_path" to storage_path,
        "metadata_json" to metadata_json,
        "updated_at" to updated_at,
    )
}

@Serializable
data class FirestoreDailyStatDto(
    val id: String,
    val user_id: String,
    val stat_date: String,
    val sessions_count: Int,
    val total_reps: Int,
    val avg_accuracy: Double,
    val updated_at: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "user_id" to user_id,
        "stat_date" to stat_date,
        "sessions_count" to sessions_count,
        "total_reps" to total_reps,
        "avg_accuracy" to avg_accuracy,
        "updated_at" to updated_at,
    )
}
