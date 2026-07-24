package com.posemonitor2.screen

import com.domain.model.CustomWorkoutRecord
import com.domain.repository.CustomWorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomWorkoutStore @Inject constructor(
    private val customWorkoutRepository: CustomWorkoutRepository,
) {

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _customWorkouts = MutableStateFlow<List<UserCustomWorkout>>(emptyList())
    val CustomWorkouts: StateFlow<List<UserCustomWorkout>> = _customWorkouts.asStateFlow()

    private var hasSeededDefault = false

    init {
        storeScope.launch {
            customWorkoutRepository.observeAll()
                .collect { workouts ->
                    _customWorkouts.value = workouts.map { it.toUi() }
                }
        }
    }

    fun ensureDefaultCustomWorkout(defaultExerciseIds: List<String>) {
        if (hasSeededDefault) return
        hasSeededDefault = true

        storeScope.launch {
            val existingDefault = customWorkoutRepository.getById(DEFAULT_CustomWorkout_ID)
            if (existingDefault != null) return@launch

            val defaultTargets = defaultExerciseIds.associateWith { DEFAULT_TARGET_REPS }
            val defaultWorkout = UserCustomWorkout(
                id = DEFAULT_CustomWorkout_ID,
                title = DEFAULT_CustomWorkout_TITLE,
                exerciseIds = defaultExerciseIds,
                exerciseTargetReps = defaultTargets,
                isDefault = true,
            )
            customWorkoutRepository.upsert(defaultWorkout.toRecord())
        }
    }

    fun getCustomWorkout(id: String): UserCustomWorkout? {
        return _customWorkouts.value.firstOrNull { it.id == id }
    }

    fun deleteCustomWorkout(id: String): UserCustomWorkout? {
        val deleted = _customWorkouts.value.firstOrNull { it.id == id } ?: return null
        storeScope.launch {
            customWorkoutRepository.deleteById(id)
        }
        return deleted
    }

    fun restoreCustomWorkout(CustomWorkout: UserCustomWorkout) {
        storeScope.launch {
            customWorkoutRepository.upsert(CustomWorkout.toRecord())
        }
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
        storeScope.launch {
            customWorkoutRepository.upsert(CustomWorkout.toRecord())
        }
        return id
    }

    fun updateCustomWorkout(
        id: String,
        title: String,
        exerciseIds: List<String>,
        exerciseTargetReps: Map<String, Int>,
    ) {
        val existingWorkout = getCustomWorkout(id) ?: return
        val now = System.currentTimeMillis()
        val updatedWorkout = existingWorkout.copy(
            title = title,
            exerciseIds = exerciseIds.distinct(),
            exerciseTargetReps = sanitizeTargets(exerciseIds, exerciseTargetReps),
            updatedAtEpochMs = now,
        )
        storeScope.launch {
            customWorkoutRepository.upsert(updatedWorkout.toRecord())
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
        storeScope.launch {
            customWorkoutRepository.upsert(duplicate.toRecord())
        }
        return duplicateId
    }

    suspend fun nextCustomWorkoutTitle(): String {
        val customCount = customWorkoutRepository.countNonDefault()
        return "My CustomWorkout ${customCount + 1}"
    }

    fun renameExercise(oldName: String, newName: String) {
        storeScope.launch {
            val allWorkouts = _customWorkouts.value
            for (workout in allWorkouts) {
                val oldIndex = workout.exerciseIds.indexOf(oldName)
                if (oldIndex == -1) continue
                val updatedIds = workout.exerciseIds.toMutableList()
                updatedIds[oldIndex] = newName
                val updatedReps = workout.exerciseTargetReps.toMutableMap()
                val reps = updatedReps.remove(oldName)
                if (reps != null) {
                    updatedReps[newName] = reps
                }
                val now = System.currentTimeMillis()
                val updated = workout.copy(
                    exerciseIds = updatedIds.distinct(),
                    exerciseTargetReps = sanitizeTargets(updatedIds, updatedReps),
                    updatedAtEpochMs = now,
                )
                customWorkoutRepository.upsert(updated.toRecord())
            }
        }
    }

    private fun sanitizeTargets(
        exerciseIds: List<String>,
        targetMap: Map<String, Int>,
    ): Map<String, Int> {
        val orderedIds = exerciseIds.distinct()
        return orderedIds.associateWith { exerciseId ->
            val providedValue = targetMap[exerciseId] ?: DEFAULT_TARGET_REPS
            providedValue.coerceIn(MIN_TARGET_REPS, MAX_TARGET_REPS)
        }
    }

    private fun UserCustomWorkout.toRecord() = CustomWorkoutRecord(
        id = id,
        title = title,
        exerciseIds = exerciseIds,
        exerciseTargetReps = exerciseTargetReps,
        isDefault = isDefault,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun CustomWorkoutRecord.toUi() = UserCustomWorkout(
        id = id,
        title = title,
        exerciseIds = exerciseIds,
        exerciseTargetReps = exerciseTargetReps,
        isDefault = isDefault,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private companion object {
        const val DEFAULT_CustomWorkout_ID = "default_CustomWorkout"
        const val DEFAULT_CustomWorkout_TITLE = "Default CustomWorkout"
        const val DEFAULT_TARGET_REPS = 12
        const val MIN_TARGET_REPS = 1
        const val MAX_TARGET_REPS = 60
    }
}
