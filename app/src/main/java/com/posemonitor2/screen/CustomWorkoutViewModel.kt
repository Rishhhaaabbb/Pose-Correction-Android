package com.posemonitor2.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.Exercise
import com.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomWorkoutViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val CustomWorkoutStore: CustomWorkoutStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomWorkoutUiState(isLoading = true))
    val uiState: StateFlow<CustomWorkoutUiState> = _uiState.asStateFlow()

    private var exercisesById: Map<String, Exercise> = emptyMap()
    private var CustomWorkoutsById: Map<String, UserCustomWorkout> = emptyMap()

    init {
        loadHubData()
    }

    private fun loadHubData() {
        viewModelScope.launch {
            runCatching {
                exerciseRepository.getAll().sortedBy { it.name }
            }.onSuccess { exercises ->
                exercisesById = exercises.associateBy { it.id }
                CustomWorkoutStore.ensureDefaultCustomWorkout(buildDefaultCustomWorkoutExerciseIds(exercises))

                CustomWorkoutStore.CustomWorkouts.collectLatest { CustomWorkouts ->
                    CustomWorkoutsById = CustomWorkouts.associateBy { it.id }
                    _uiState.value = CustomWorkoutUiState(
                        CustomWorkouts = CustomWorkouts
                            .map { CustomWorkout -> CustomWorkout.toUiItem(exercisesById) }
                            .sortedWith(compareByDescending<CustomWorkoutListItemUi> { it.isDefault }.thenBy { it.title }),
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.value = CustomWorkoutUiState(
                    isLoading = false,
                    errorMessage = throwable.message ?: "Failed to load CustomWorkouts",
                )
            }
        }
    }

    fun deleteCustomWorkout(CustomWorkoutId: String): UserCustomWorkout? {
        return CustomWorkoutStore.deleteCustomWorkout(CustomWorkoutId)
    }

    fun undoDelete(deletedCustomWorkout: UserCustomWorkout) {
        CustomWorkoutStore.restoreCustomWorkout(deletedCustomWorkout)
    }

    fun duplicateCustomWorkout(CustomWorkoutId: String): String? {
        return CustomWorkoutStore.duplicateCustomWorkout(CustomWorkoutId)
    }

    fun getStartExerciseName(CustomWorkoutId: String): String? {
        val CustomWorkout = CustomWorkoutsById[CustomWorkoutId] ?: return null
        val firstExerciseId = CustomWorkout.exerciseIds.firstOrNull() ?: return null
        return exercisesById[firstExerciseId]?.name
    }

    fun getCustomWorkoutExerciseNames(CustomWorkoutId: String): List<String>? {
        val CustomWorkout = CustomWorkoutsById[CustomWorkoutId] ?: return null
        return CustomWorkout.exerciseIds.mapNotNull { exercisesById[it]?.name }
    }

    fun getTargetRepsForExercise(CustomWorkoutId: String, exerciseName: String): Int {
        val CustomWorkout = CustomWorkoutsById[CustomWorkoutId] ?: return DEFAULT_TARGET_REPS
        // Find the exercise ID by matching the exercise name
        val exerciseId = exercisesById.entries
            .firstOrNull { it.value.name == exerciseName }?.key
            ?: return DEFAULT_TARGET_REPS
        return CustomWorkout.exerciseTargetReps[exerciseId] ?: DEFAULT_TARGET_REPS
    }

    private fun buildDefaultCustomWorkoutExerciseIds(exercises: List<Exercise>): List<String> {
        val exerciseById = exercises.associateBy { it.id }
        val defaults = DEFAULT_CustomWorkout_EXERCISE_IDS.mapNotNull { exerciseById[it]?.id }
        return defaults.ifEmpty { exercises.take(DEFAULT_CustomWorkout_FALLBACK_SIZE).map { it.id } }
    }

    private fun UserCustomWorkout.toUiItem(exerciseMap: Map<String, Exercise>): CustomWorkoutListItemUi {
        val names = exerciseIds.mapNotNull { id -> exerciseMap[id]?.name }
        val totalTargetReps = exerciseIds.sumOf { exerciseId ->
            exerciseTargetReps[exerciseId] ?: DEFAULT_TARGET_REPS
        }
        val preview = when {
            names.isEmpty() -> "No exercises selected"
            names.size <= 2 -> names.joinToString(" - ") { formatExerciseName(it) }
            else -> {
                val visible = names.take(2).joinToString(" - ") { formatExerciseName(it) }
                "$visible +${names.size - 2}"
            }
        }

        val estimatedMinutes = estimateMinutes(
            exerciseCount = names.size,
            totalTargetReps = totalTargetReps,
        )

        return CustomWorkoutListItemUi(
            id = id,
            title = title,
            previewText = preview,
            exerciseCount = names.size,
            estimatedMinutes = estimatedMinutes,
            isDefault = isDefault,
            canStart = names.isNotEmpty(),
        )
    }

    private fun estimateMinutes(exerciseCount: Int, totalTargetReps: Int): Int {
        if (exerciseCount == 0) return 0
        val repMinutes = kotlin.math.ceil(totalTargetReps / REPS_PER_MINUTE).toInt()
        val transitionMinutes = (exerciseCount - 1) * TRANSITION_MIN_PER_EXERCISE
        return (repMinutes + transitionMinutes).coerceAtLeast(1)
    }

    private fun formatExerciseName(name: String): String {
        return name
            .split('-')
            .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }
    }

    private companion object {
        val DEFAULT_CustomWorkout_EXERCISE_IDS = listOf("squat", "pushup", "plank")
        const val DEFAULT_CustomWorkout_FALLBACK_SIZE = 3
        const val DEFAULT_TARGET_REPS = 12
        const val REPS_PER_MINUTE = 18.0
        const val TRANSITION_MIN_PER_EXERCISE = 1
    }
}

data class CustomWorkoutUiState(
    val CustomWorkouts: List<CustomWorkoutListItemUi> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
