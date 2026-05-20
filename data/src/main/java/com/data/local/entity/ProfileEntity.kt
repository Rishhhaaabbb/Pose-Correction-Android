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
)
