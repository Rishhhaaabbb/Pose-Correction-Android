package com.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_metrics")
data class SessionMetricEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "metric_name")
    val metricName: String,
    @ColumnInfo(name = "metric_value")
    val metricValue: Double,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)
