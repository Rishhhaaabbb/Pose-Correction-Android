package com.domain.model

data class OnboardingResponse(
    val userId: String,
    val ageGroup: String?,
    val gender: String?,
    val goal: String?,
    val experienceLevel: String?,
    val yogaStyle: String?,
    val flexibilityLevel: String?,
    val focusAreas: String?,         // comma-separated
    val heightCm: Int?,
    val weightKg: Double?,
    val dailyPracticeMinutes: Int?,
    val weeklyFrequency: String?,
    val injuries: String?,           // comma-separated; "none" if None selected
    val medicalConditions: String?,  // comma-separated
    val cameraPermissionGranted: Boolean?,
    val isCompleted: Boolean,
    val updatedAtEpochMs: Long,
)