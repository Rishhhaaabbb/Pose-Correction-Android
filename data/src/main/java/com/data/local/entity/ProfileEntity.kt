package com.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    val email: String?,
    @ColumnInfo(name = "profile_image_url")
    val profileImageUrl: String?,
    @ColumnInfo(name = "height_cm")
    val heightCm: Int?,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double?,
    @ColumnInfo(name = "fitness_goal")
    val fitnessGoal: String?,
    @ColumnInfo(name = "target_weight_kg")
    val targetWeightKg: Double?,
    @ColumnInfo(name = "weekly_goal_sessions")
    val weeklyGoalSessions: Int?,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
    // Questionnaire fields (added in migration 5→6)
    @ColumnInfo(name = "age_group")
    val ageGroup: String? = null,
    val gender: String? = null,
    @ColumnInfo(name = "experience_level")
    val experienceLevel: String? = null,
    @ColumnInfo(name = "yoga_style")
    val yogaStyle: String? = null,
    @ColumnInfo(name = "flexibility_level")
    val flexibilityLevel: String? = null,
    @ColumnInfo(name = "focus_areas")
    val focusAreas: String? = null,
    @ColumnInfo(name = "daily_practice_minutes")
    val dailyPracticeMinutes: Int? = null,
    @ColumnInfo(name = "weekly_frequency")
    val weeklyFrequency: String? = null,
    val injuries: String? = null,
    @ColumnInfo(name = "medical_conditions")
    val medicalConditions: String? = null,
    @ColumnInfo(name = "camera_permission_granted")
    val cameraPermissionGranted: Int? = null,
)
