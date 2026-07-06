package com.posemonitor2.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.Exercise
import com.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomWorkoutEditorViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val CustomWorkoutStore: CustomWorkoutStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val CustomWorkoutIdArg: String = savedStateHandle.get<String>("CustomWorkoutId") ?: NEW_WORKOUT_ID

    private val _uiState = MutableStateFlow(CustomWorkoutEditorUiState(isLoading = true))
    val uiState: StateFlow<CustomWorkoutEditorUiState> = _uiState.asStateFlow()

    private var activeCustomWorkoutId: String? = null

    init {
        loadEditorData()
    }

    private fun loadEditorData() {
        viewModelScope.launch {
            runCatching {
                exerciseRepository.getAll().sortedBy { it.name }
            }.onSuccess { allExercises ->
                CustomWorkoutStore.ensureDefaultCustomWorkout(buildDefaultCustomWorkoutExerciseIds(allExercises))
                val exerciseMap = allExercises.associateBy { it.id }

                val existingCustomWorkout = if (CustomWorkoutIdArg == NEW_WORKOUT_ID) {
                    null
                } else {
                    CustomWorkoutStore.getCustomWorkout(CustomWorkoutIdArg)
                }

                activeCustomWorkoutId = existingCustomWorkout?.id

                val selectedExercises = existingCustomWorkout
                    ?.exerciseIds
                    ?.mapNotNull { id -> exerciseMap[id] }
                    .orEmpty()

                val selectedTargets = selectedExercises.associate { exercise ->
                    exercise.id to (existingCustomWorkout?.exerciseTargetReps?.get(exercise.id) ?: DEFAULT_TARGET_REPS)
                }

                val title = existingCustomWorkout?.title ?: CustomWorkoutStore.nextCustomWorkoutTitle()

                _uiState.value = CustomWorkoutEditorUiState(
                    CustomWorkoutId = activeCustomWorkoutId,
                    title = title,
                    selectedExercises = selectedExercises,
                    selectedExerciseTargetReps = selectedTargets,
                    allExercises = allExercises,
                    categories = allExercises.mapNotNull { it.category }.distinct().sorted(),
                    difficulties = allExercises.mapNotNull { it.difficulty }.distinct().sorted(),
                    isNewCustomWorkout = existingCustomWorkout == null,
                    isLoading = false,
                    errorMessage = null,
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.message ?: "Failed to load CustomWorkout editor",
                )
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun addExercise(exerciseId: String) {
        val state = _uiState.value
        val exercise = state.allExercises.firstOrNull { it.id == exerciseId } ?: return
        if (state.selectedExercises.any { it.id == exerciseId }) return

        _uiState.value = state.copy(
            selectedExercises = state.selectedExercises + exercise,
            selectedExerciseTargetReps = state.selectedExerciseTargetReps +
                (exercise.id to DEFAULT_TARGET_REPS),
        )
    }

    fun removeExercise(exerciseId: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedExercises = state.selectedExercises.filterNot { it.id == exerciseId },
            selectedExerciseTargetReps = state.selectedExerciseTargetReps - exerciseId,
        )
    }

    fun moveExerciseUp(exerciseId: String) {
        val state = _uiState.value
        val currentIndex = state.selectedExercises.indexOfFirst { it.id == exerciseId }
        if (currentIndex <= 0) return

        val mutable = state.selectedExercises.toMutableList()
        val current = mutable[currentIndex]
        mutable[currentIndex] = mutable[currentIndex - 1]
        mutable[currentIndex - 1] = current
        _uiState.value = state.copy(selectedExercises = mutable)
    }

    fun moveExerciseDown(exerciseId: String) {
        val state = _uiState.value
        val currentIndex = state.selectedExercises.indexOfFirst { it.id == exerciseId }
        if (currentIndex == -1 || currentIndex >= state.selectedExercises.lastIndex) return

        val mutable = state.selectedExercises.toMutableList()
        val current = mutable[currentIndex]
        mutable[currentIndex] = mutable[currentIndex + 1]
        mutable[currentIndex + 1] = current
        _uiState.value = state.copy(selectedExercises = mutable)
    }

    fun increaseTargetReps(exerciseId: String) {
        val state = _uiState.value
        val current = state.selectedExerciseTargetReps[exerciseId] ?: DEFAULT_TARGET_REPS
        val updated = (current + TARGET_REPS_STEP).coerceAtMost(MAX_TARGET_REPS)
        updateTargetReps(exerciseId = exerciseId, value = updated)
    }

    fun decreaseTargetReps(exerciseId: String) {
        val state = _uiState.value
        val current = state.selectedExerciseTargetReps[exerciseId] ?: DEFAULT_TARGET_REPS
        val updated = (current - TARGET_REPS_STEP).coerceAtLeast(MIN_TARGET_REPS)
        updateTargetReps(exerciseId = exerciseId, value = updated)
    }

    fun updateTargetRepsFromInput(exerciseId: String, rawValue: String) {
        val parsed = rawValue.trim().toIntOrNull() ?: return
        if (parsed <= 0) return
        val updated = parsed.coerceAtMost(MAX_TARGET_REPS)
        updateTargetReps(exerciseId = exerciseId, value = updated)
    }

    private fun updateTargetReps(exerciseId: String, value: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedExerciseTargetReps = state.selectedExerciseTargetReps +
                (exerciseId to value),
        )
    }

    fun getAddableExercises(
        searchQuery: String,
        selectedDifficulty: String?,
        selectedCategory: String?,
    ): List<Exercise> {
        val state = _uiState.value
        val selectedIds = state.selectedExercises.map { it.id }.toSet()

        return state.allExercises.filter { exercise ->
            if (exercise.id in selectedIds) return@filter false

            val matchesSearch = searchQuery.isBlank() ||
                exercise.name.contains(searchQuery, ignoreCase = true)
            val matchesDifficulty = selectedDifficulty == null ||
                exercise.difficulty == selectedDifficulty
            val matchesCategory = selectedCategory == null ||
                exercise.category == selectedCategory

            matchesSearch && matchesDifficulty && matchesCategory
        }
    }

    fun saveCustomWorkout(): String {
        val state = _uiState.value
        val normalizedTitle = state.title.trim().ifBlank { CustomWorkoutStore.nextCustomWorkoutTitle() }
        val exerciseIds = state.selectedExercises.map { it.id }
        val targetMap = exerciseIds.associateWith { exerciseId ->
            (state.selectedExerciseTargetReps[exerciseId] ?: DEFAULT_TARGET_REPS)
                .coerceIn(MIN_TARGET_REPS, MAX_TARGET_REPS)
        }

        val CustomWorkoutId = activeCustomWorkoutId
        val savedId = if (CustomWorkoutId == null) {
            CustomWorkoutStore.createCustomWorkout(
                title = normalizedTitle,
                exerciseIds = exerciseIds,
                exerciseTargetReps = targetMap,
            )
        } else {
            CustomWorkoutStore.updateCustomWorkout(
                id = CustomWorkoutId,
                title = normalizedTitle,
                exerciseIds = exerciseIds,
                exerciseTargetReps = targetMap,
            )
            CustomWorkoutId
        }

        activeCustomWorkoutId = savedId
        _uiState.value = state.copy(
            CustomWorkoutId = savedId,
            title = normalizedTitle,
            isNewCustomWorkout = false,
        )
        return savedId
    }

    fun getStartExerciseName(): String? {
        return _uiState.value.selectedExercises.firstOrNull()?.name
    }

    private fun buildDefaultCustomWorkoutExerciseIds(exercises: List<Exercise>): List<String> {
        val exerciseById = exercises.associateBy { it.id }
        val defaults = DEFAULT_CustomWorkout_EXERCISE_IDS.mapNotNull { exerciseById[it]?.id }
        return defaults.ifEmpty { exercises.take(DEFAULT_CustomWorkout_FALLBACK_SIZE).map { it.id } }
    }

    private companion object {
        val DEFAULT_CustomWorkout_EXERCISE_IDS = listOf("squat", "pushup", "plank")
        const val DEFAULT_CustomWorkout_FALLBACK_SIZE = 3
        const val DEFAULT_TARGET_REPS = 12
        const val MIN_TARGET_REPS = 4
        const val MAX_TARGET_REPS = 60
        const val TARGET_REPS_STEP = 1
    }
}

data class CustomWorkoutEditorUiState(
    val CustomWorkoutId: String? = null,
    val title: String = "",
    val selectedExercises: List<Exercise> = emptyList(),
    val selectedExerciseTargetReps: Map<String, Int> = emptyMap(),
    val allExercises: List<Exercise> = emptyList(),
    val categories: List<String> = emptyList(),
    val difficulties: List<String> = emptyList(),
    val isNewCustomWorkout: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
