package com.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_workouts")
data class CustomWorkoutEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "exercise_ids")
    val exerciseIds: String, // JSON array or comma-separated
    @ColumnInfo(name = "exercise_target_reps")
    val exerciseTargetReps: String, // JSON map
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)
