package com.domain.model

data class CustomWorkoutRecord(
    val id: String,
    val title: String,
    val exerciseIds: List<String>,
    val exerciseTargetReps: Map<String, Int>,
    val isDefault: Boolean,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)
