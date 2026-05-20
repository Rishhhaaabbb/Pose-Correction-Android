package com.posemonitor2.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.AuthUser
import com.domain.model.Exercise
import com.domain.model.UserProfile
import com.domain.repository.ExerciseRepository
import com.domain.usecase.ObserveAuthSessionUseCase
import com.domain.usecase.ObserveProfileUseCase
import com.domain.usecase.RestoreSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ExercisesViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    private val observeProfileUseCase: ObserveProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExercisesUiState())
    val uiState: StateFlow<ExercisesUiState> = _uiState.asStateFlow()

    init {
        loadExercises()
        loadUserProfile()
    }

    private fun loadExercises() {
        viewModelScope.launch {
            exerciseRepository.observeAll().collect { exercises ->
                val categories = exercises.mapNotNull { it.category }.distinct().sorted()
                val difficulties = exercises.mapNotNull { it.difficulty }.distinct().sorted()
                
                _uiState.value = _uiState.value.copy(
                    exercises = exercises,
                    categories = categories,
                    difficulties = difficulties,
                )
            }
        }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            restoreSessionUseCase()
        }

        viewModelScope.launch {
            observeAuthSessionUseCase()
                .flatMapLatest { authUser ->
                    val userId = authUser?.id
                    if (userId.isNullOrBlank()) {
                        flowOf(ProfileInfo())
                    } else {
                        combine(observeProfileUseCase(userId), flowOf(authUser)) { profile, auth ->
                            ProfileInfo(
                                displayName = profile?.displayName?.takeIf(String::isNotBlank)
                                    ?: auth.fullName?.takeIf(String::isNotBlank)
                                    ?: auth.email?.substringBefore('@')
                                    ?: "User",
                                initial = (profile?.displayName?.firstOrNull()
                                    ?: auth.fullName?.firstOrNull()
                                    ?: auth.email?.firstOrNull()
                                    ?: 'U').uppercaseChar(),
                                isAdmin = isAdminEmail(auth.email)
                            )
                        }
                    }
                }
                .collect { profile ->
                    _uiState.value = _uiState.value.copy(userProfile = profile)
                }
        }
    }

    private fun isAdminEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val normalized = email.trim().lowercase().replace(" ", "")
        return normalized == "admin" || normalized == "admin@poseapp.com"
    }

    fun getFilteredExercises(
        searchQuery: String,
        selectedDifficulty: String?,
        selectedCategory: String?,
    ): List<Exercise> {
        val exercises = _uiState.value.exercises
        return exercises.filter { exercise ->
            val matchesSearch = searchQuery.isBlank() || 
                exercise.name.contains(searchQuery, ignoreCase = true)
            val matchesDifficulty = selectedDifficulty == null || 
                selectedDifficulty == "All Levels" ||
                exercise.difficulty == selectedDifficulty
            val matchesCategory = selectedCategory == null || 
                selectedCategory == "All" ||
                exercise.category == selectedCategory
            matchesSearch && matchesDifficulty && matchesCategory
        }
    }
}

data class ExercisesUiState(
    val exercises: List<Exercise> = emptyList(),
    val categories: List<String> = emptyList(),
    val difficulties: List<String> = emptyList(),
    val userProfile: ProfileInfo = ProfileInfo(),
    val isLoading: Boolean = false,
)

data class ProfileInfo(
    val displayName: String = "User",
    val initial: Char = 'U',
    val isAdmin: Boolean = false,
)