package com.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "onboarding_responses")
data class OnboardingResponseEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "age_group")
    val ageGroup: String?,
    val gender: String?,
    val goal: String?,
    @ColumnInfo(name = "experience_level")
    val experienceLevel: String?,
    @ColumnInfo(name = "yoga_style")
    val yogaStyle: String?,
    @ColumnInfo(name = "flexibility_level")
    val flexibilityLevel: String?,
    @ColumnInfo(name = "focus_areas")
    val focusAreas: String?,
    @ColumnInfo(name = "height_cm")
    val heightCm: Int?,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double?,
    @ColumnInfo(name = "daily_practice_minutes")
    val dailyPracticeMinutes: Int?,
    @ColumnInfo(name = "weekly_frequency")
    val weeklyFrequency: String?,
    val injuries: String?,
    @ColumnInfo(name = "medical_conditions")
    val medicalConditions: String?,
    @ColumnInfo(name = "camera_permission_granted")
    val cameraPermissionGranted: Int?,   // 0 or 1 (Room stores Boolean as Int)
    @ColumnInfo(name = "is_completed")
    val isCompleted: Int,                // 0 or 1, NOT NULL DEFAULT 0
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
