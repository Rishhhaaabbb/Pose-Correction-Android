package com.posemonitor2.screen

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.AuthUser
import com.domain.model.Exercise
import com.domain.model.UserProfile
import com.domain.repository.ExerciseRepository
import com.domain.usecase.ObserveAuthSessionUseCase
import com.domain.usecase.ObserveProfileUseCase
import com.domain.usecase.RestoreSessionUseCase
import com.posemonitor2.loadAvailableGraphs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ExercisesViewModel @Inject constructor(
    application: Application,
    private val exerciseRepository: ExerciseRepository,
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    private val observeProfileUseCase: ObserveProfileUseCase,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExercisesUiState())
    val uiState: StateFlow<ExercisesUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val selectedDifficulty = MutableStateFlow<String?>(null)
    private val selectedCategory = MutableStateFlow<String?>(null)

    val filteredExercises: StateFlow<List<Exercise>> =
        combine(uiState, _searchQuery, selectedDifficulty, selectedCategory) { state, query, diff, cat ->
            val exercises = state.exercises
            exercises.filter { exercise ->
                val matchesSearch = query.isBlank() ||
                    exercise.name.contains(query, ignoreCase = true) ||
                    (exercise.category?.contains(query, ignoreCase = true) == true)
                val matchesDifficulty = diff == null || diff == "All Levels" || exercise.difficulty == diff
                val matchesCategory = cat == null || cat == "All" || exercise.category == cat
                matchesSearch && matchesDifficulty && matchesCategory
            }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadExercises()
        loadUserProfile()
        loadGraphs()
    }

    private fun loadExercises() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            exerciseRepository.observeAll().collect { exercises ->
                val categories = exercises.mapNotNull { it.category }.distinct().sorted()
                val difficulties = exercises.mapNotNull { it.difficulty }.distinct().sorted()
                val drawableMap = withContext(Dispatchers.Default) {
                    val resources = getApplication<Application>().resources
                    val packageName = getApplication<Application>().packageName
                    exercises.associate { exercise ->
                        val name = exercise.id.replace("-", "_")
                        exercise.id to resources.getIdentifier(name, "drawable", packageName)
                    }
                }
                val thumbnails = withContext(Dispatchers.Default) {
                    val resources = getApplication<Application>().resources
                    val density = resources.displayMetrics.density
                    val targetPx = (80 * density).toInt()
                    exercises.associate { exercise ->
                        val resId = drawableMap[exercise.id] ?: 0
                        val bitmap = if (resId != 0) {
                            val opts = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeResource(resources, resId, opts)
                            val sampleSize = (opts.outWidth / targetPx).coerceAtLeast(1)
                            val decodeOpts = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize
                            }
                            val bmp = BitmapFactory.decodeResource(resources, resId, decodeOpts)
                            bmp?.asImageBitmap()
                        } else null
                        exercise.id to bitmap
                    }
                }

                _uiState.value = _uiState.value.copy(
                    exercises = exercises,
                    categories = categories,
                    difficulties = difficulties,
                    drawableByExerciseId = drawableMap,
                    preloadedThumbnails = thumbnails,
                    isLoading = false,
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

    private fun loadGraphs() {
        viewModelScope.launch(Dispatchers.IO) {
            val graphs = loadAvailableGraphs(getApplication())
            _uiState.value = _uiState.value.copy(
                graphs = graphs,
                graphByFileName = graphs.associateBy { it.fileName },
            )
        }
    }

    private fun isAdminEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val normalized = email.trim().lowercase().replace(" ", "")
        return normalized == "admin" || normalized == "admin@poseapp.com"
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun updateFilters(
        search: String,
        difficulty: String?,
        category: String?,
    ) {
        _searchQuery.value = search
        selectedDifficulty.value = difficulty
        selectedCategory.value = category
    }
}

data class ExercisesUiState(
    val exercises: List<Exercise> = emptyList(),
    val categories: List<String> = emptyList(),
    val difficulties: List<String> = emptyList(),
    val userProfile: ProfileInfo = ProfileInfo(),
    val graphs: List<com.posemonitor2.GraphEntry> = emptyList(),
    val graphByFileName: Map<String, com.posemonitor2.GraphEntry> = emptyMap(),
    val drawableByExerciseId: Map<String, Int> = emptyMap(),
    val preloadedThumbnails: Map<String, ImageBitmap?> = emptyMap(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
)

data class ProfileInfo(
    val displayName: String = "User",
    val initial: Char = 'U',
    val isAdmin: Boolean = false,
)
