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
    // Questionnaire fields
    val ageGroup: String? = null,
    val gender: String? = null,
    val experienceLevel: String? = null,
    val yogaStyle: String? = null,
    val flexibilityLevel: String? = null,
    val focusAreas: String? = null,
    val dailyPracticeMinutes: Int? = null,
    val weeklyFrequency: String? = null,
    val injuries: String? = null,
    val medicalConditions: String? = null,
    val cameraPermissionGranted: Boolean? = null,
)
