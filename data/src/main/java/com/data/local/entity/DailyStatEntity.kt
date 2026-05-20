package com.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "stat_date")
    val statDate: String,
    @ColumnInfo(name = "sessions_count")
    val sessionsCount: Int,
    @ColumnInfo(name = "total_reps")
    val totalReps: Int,
    @ColumnInfo(name = "avg_accuracy")
    val avgAccuracy: Double,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)
