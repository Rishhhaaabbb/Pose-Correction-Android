package com.data.remote.supabase

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SupabaseAuthResponse(
    val access_token: String,
    val refresh_token: String,
    val user: SupabaseUserDto,
)

@Serializable
data class SupabaseUserDto(
    val id: String,
    val email: String? = null,
    val user_metadata: JsonObject? = null,
)

@Serializable
data class SupabaseRefreshRequest(
    val refresh_token: String,
)

@Serializable
data class SupabaseIdTokenRequest(
    val provider: String,
    val id_token: String,
)

@Serializable
data class SupabasePhoneOtpRequest(
    val phone: String,
    val create_user: Boolean = true,
    val channel: String = "sms",
)

@Serializable
data class SupabaseVerifyPhoneOtpRequest(
    val phone: String,
    val token: String,
    val type: String = "sms",
)

@Serializable
data class SupabaseProfileDto(
    val id: String,
    val display_name: String,
    val email: String? = null,
    val profile_image_url: String? = null,
    val height_cm: Int? = null,
    val weight_kg: Double? = null,
    val fitness_goal: String? = null,
    val target_weight_kg: Double? = null,
    val weekly_goal_sessions: Int? = null,
    val updated_at: Long,
)

@Serializable
data class SupabaseSessionDto(
    val id: String,
    val user_id: String,
    val exercise_name: String? = null,
    val total_reps: Int? = null,
    val accuracy_percent: Double? = null,
    val started_at: Long,
    val ended_at: Long? = null,
    val duration_sec: Int,
    val status: String,
    val updated_at: Long,
)

@Serializable
data class SupabaseSessionMetricDto(
    val id: String,
    val session_id: String,
    val user_id: String,
    val metric_name: String,
    val metric_value: Double,
    val updated_at: Long,
)

@Serializable
data class SupabasePoseGraphDto(
    val id: String,
    val user_id: String,
    val graph_name: String,
    val storage_path: String,
    val metadata_json: String? = null,
    val updated_at: Long,
)

@Serializable
data class SupabaseDailyStatDto(
    val id: String,
    val user_id: String,
    val stat_date: String,
    val sessions_count: Int,
    val total_reps: Int,
    val avg_accuracy: Double,
    val updated_at: Long,
)
