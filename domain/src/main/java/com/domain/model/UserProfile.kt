package com.domain.model

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String?,
    val profileImageUrl: String?,
    val heightCm: Int?,
    val weightKg: Double?,
    val fitnessGoal: String?,
    val targetWeightKg: Double?,
    val weeklyGoalSessions: Int?,
)
