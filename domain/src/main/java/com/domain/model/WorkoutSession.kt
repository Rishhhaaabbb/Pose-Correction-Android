package com.domain.model

enum class SessionStatus {
    COMPLETED,
    CANCELLED,
    DRAFT,
    PARTIAL,
}

data class WorkoutSession(
    val id: String,
    val userId: String,
    val exerciseName: String? = null,
    val targetReps: Int? = null,
    val totalReps: Int? = null,
    val accuracyPercent: Double? = null,
    val totalCalories: Double? = null,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val durationSec: Int,
    val status: SessionStatus,
)
