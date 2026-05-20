package com.posemonitor2.screen

data class UserCustomWorkout(
    val id: String,
    val title: String,
    val exerciseIds: List<String>,
    val exerciseTargetReps: Map<String, Int> = emptyMap(),
    val isDefault: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

data class CustomWorkoutListItemUi(
    val id: String,
    val title: String,
    val previewText: String,
    val exerciseCount: Int,
    val estimatedMinutes: Int,
    val isDefault: Boolean,
    val canStart: Boolean,
)
