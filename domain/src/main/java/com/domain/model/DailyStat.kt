package com.domain.model

data class DailyStat(
    val id: String,
    val userId: String,
    val statDate: String,
    val sessionsCount: Int,
    val totalReps: Int,
    val avgAccuracy: Double,
)
