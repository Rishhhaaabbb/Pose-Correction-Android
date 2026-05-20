package com.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "exercise_name")
    val exerciseName: String?,
    @ColumnInfo(name = "target_reps")
    val targetReps: Int?,
    @ColumnInfo(name = "total_reps")
    val totalReps: Int?,
    @ColumnInfo(name = "accuracy_percent")
    val accuracyPercent: Double?,
    @ColumnInfo(name = "total_calories")
    val totalCalories: Double?,
    @ColumnInfo(name = "started_at")
    val startedAtEpochMs: Long,
    @ColumnInfo(name = "ended_at")
    val endedAtEpochMs: Long?,
    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,
    val status: String,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)
