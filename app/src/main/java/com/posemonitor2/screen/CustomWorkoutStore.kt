package com.posemonitor2.screen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomWorkoutStore @Inject constructor() {

    private val _CustomWorkouts = MutableStateFlow<List<UserCustomWorkout>>(emptyList())
    val CustomWorkouts: StateFlow<List<UserCustomWorkout>> = _CustomWorkouts.asStateFlow()

    private var hasSeededDefault = false

    fun ensureDefaultCustomWorkout(defaultExerciseIds: List<String>) {
        if (hasSeededDefault) return
        hasSeededDefault = true

        val defaultTargets = defaultExerciseIds.associateWith { DEFAULT_TARGET_REPS }

        _CustomWorkouts.value = listOf(
            UserCustomWorkout(
                id = DEFAULT_CustomWorkout_ID,
                title = DEFAULT_CustomWorkout_TITLE,
                exerciseIds = defaultExerciseIds,
                exerciseTargetReps = defaultTargets,
                isDefault = true,
            )
        )
    }

    fun getCustomWorkout(id: String): UserCustomWorkout? {
        return _CustomWorkouts.value.firstOrNull { it.id == id }
    }

    fun deleteCustomWorkout(id: String): UserCustomWorkout? {
        val deleted = _CustomWorkouts.value.firstOrNull { it.id == id } ?: return null
        _CustomWorkouts.value = _CustomWorkouts.value.filterNot { it.id == id }
        return deleted
    }

    fun restoreCustomWorkout(CustomWorkout: UserCustomWorkout) {
        _CustomWorkouts.value = (_CustomWorkouts.value + CustomWorkout)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAtEpochMs }
    }

    fun createCustomWorkout(
        title: String,
        exerciseIds: List<String>,
        exerciseTargetReps: Map<String, Int>,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val CustomWorkout = UserCustomWorkout(
            id = id,
            title = title,
            exerciseIds = exerciseIds.distinct(),
            exerciseTargetReps = sanitizeTargets(exerciseIds, exerciseTargetReps),
            isDefault = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        _CustomWorkouts.value = _CustomWorkouts.value + CustomWorkout
        return id
    }

    fun updateCustomWorkout(
        id: String,
        title: String,
        exerciseIds: List<String>,
        exerciseTargetReps: Map<String, Int>,
    ) {
        val now = System.currentTimeMillis()
        _CustomWorkouts.value = _CustomWorkouts.value.map { CustomWorkout ->
            if (CustomWorkout.id != id) return@map CustomWorkout
            CustomWorkout.copy(
                title = title,
                exerciseIds = exerciseIds.distinct(),
                exerciseTargetReps = sanitizeTargets(exerciseIds, exerciseTargetReps),
                updatedAtEpochMs = now,
            )
        }
    }

    fun duplicateCustomWorkout(id: String): String? {
        val source = getCustomWorkout(id) ?: return null
        val now = System.currentTimeMillis()
        val duplicateId = UUID.randomUUID().toString()
        val duplicate = source.copy(
            id = duplicateId,
            title = "${source.title} Copy",
            isDefault = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        _CustomWorkouts.value = _CustomWorkouts.value + duplicate
        return duplicateId
    }

    fun nextCustomWorkoutTitle(): String {
        val customCount = _CustomWorkouts.value.count { !it.isDefault }
        return "My CustomWorkout ${customCount + 1}"
    }

    private fun sanitizeTargets(
        exerciseIds: List<String>,
        targetMap: Map<String, Int>,
    ): Map<String, Int> {
        val orderedIds = exerciseIds.distinct()
        return orderedIds.associateWith { exerciseId ->
            val raw = targetMap[exerciseId] ?: DEFAULT_TARGET_REPS
            raw.coerceIn(MIN_TARGET_REPS, MAX_TARGET_REPS)
        }
    }

    private companion object {
        const val DEFAULT_CustomWorkout_ID = "default_CustomWorkout"
        const val DEFAULT_CustomWorkout_TITLE = "Default CustomWorkout"
        const val DEFAULT_TARGET_REPS = 12
        const val MIN_TARGET_REPS = 4
        const val MAX_TARGET_REPS = 60
    }
}
