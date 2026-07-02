package com.posemonitor2.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.AuthUser
import com.domain.model.UserProfile
import com.domain.model.WorkoutSession
import com.domain.repository.OnboardingRepository
import com.domain.usecase.EnsureProfileUseCase
import com.domain.usecase.ObserveAuthSessionUseCase
import com.domain.usecase.ObserveProfileUseCase
import com.domain.usecase.ObserveWorkoutSessionsUseCase
import com.domain.usecase.RefreshProfileUseCase
import com.domain.usecase.RestoreSessionUseCase
import com.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel @Inject constructor(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val ensureProfileUseCase: EnsureProfileUseCase,
    private val refreshProfileUseCase: RefreshProfileUseCase,
    private val observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    private val observeProfileUseCase: ObserveProfileUseCase,
    private val observeWorkoutSessionsUseCase: ObserveWorkoutSessionsUseCase,
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            restoreSessionUseCase()
        }

        viewModelScope.launch {
            observeAuthSessionUseCase()
                .flatMapLatest { authUser ->
                    val userId = authUser?.id
                    if (userId.isNullOrBlank()) {
                        flowOf(ProfileUiState(isLoading = false))
                    } else {
                        // Pull latest profile from Firestore into Room on every login
                        refreshProfileUseCase(userId)
                        combine(
                            observeProfileUseCase(userId),
                            observeWorkoutSessionsUseCase(userId, limit = 250),
                            onboardingRepository.observeAnswers(userId),
                        ) { profile, sessions, onboarding ->
                            buildUiState(authUser, profile, sessions, onboarding)
                        }
                    }
                }
                .collect { newState ->
                    _uiState.update {
                        newState.copy(
                            dailyReminders = it.dailyReminders,
                            goalAchievements = it.goalAchievements,
                            signOutCompleted = it.signOutCompleted,
                            saveCompleted = it.saveCompleted,
                            saveErrorMessage = it.saveErrorMessage,
                        )
                    }
                }
        }
    }

    fun setDailyReminders(enabled: Boolean) {
        _uiState.update { it.copy(dailyReminders = enabled) }
    }

    fun setGoalAchievements(enabled: Boolean) {
        _uiState.update { it.copy(goalAchievements = enabled) }
    }

    fun clearSignOutSignal() {
        _uiState.update { it.copy(signOutCompleted = false) }
    }

    fun clearSaveSignal() {
        _uiState.update { it.copy(saveCompleted = false, saveErrorMessage = null) }
    }

    fun setProfileImageUri(uri: String?) {
        _uiState.update { it.copy(profileImageUri = uri) }
    }

    fun saveProfileEdits(
        displayName: String,
        heightCm: Int?,
        weightKg: Double?,
        fitnessGoal: String,
        targetWeightKg: Double?,
        weeklyGoalSessions: Int?,
    ) {
        val state = _uiState.value
        val userId = state.userId ?: return
        val currentImage = state.profileImageUri

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveErrorMessage = null) }
            ensureProfileUseCase(
                UserProfile(
                    id = userId,
                    displayName = displayName.ifBlank { "User" },
                    email = state.email,
                    profileImageUrl = currentImage,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    fitnessGoal = fitnessGoal.ifBlank { "General Fitness" },
                    targetWeightKg = targetWeightKg,
                    weeklyGoalSessions = weeklyGoalSessions,
                )
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, saveCompleted = true) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        saveCompleted = false,
                        saveErrorMessage = err.message ?: "Unable to save profile",
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            signOutUseCase()
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, signOutCompleted = true) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    private fun buildUiState(
        authUser: AuthUser,
        profile: UserProfile?,
        sessions: List<WorkoutSession>,
        onboarding: com.domain.model.OnboardingResponse? = null,
    ): ProfileUiState {
        val displayName = profile?.displayName?.takeIf(String::isNotBlank)
            ?: authUser.fullName?.takeIf(String::isNotBlank)
            ?: authUser.email?.substringBefore('@')
            ?: "User"

        val memberSince = memberSinceText(sessions)
        val heightCm = profile?.heightCm
        val weightKg = profile?.weightKg
        val bmi = calculateBmi(heightCm, weightKg)

        val sessionsLast7 = sessionsLastDays(sessions, 7)
        val weeklyGoalSessions = profile?.weeklyGoalSessions?.coerceAtLeast(1) ?: 4
        val weeklyProgress = if (weeklyGoalSessions == 0) 0 else {
            ((sessionsLast7.size.toDouble() / weeklyGoalSessions.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
        }

        val preferredExercises = sessions
            .groupBy { it.exerciseName?.takeIf(String::isNotBlank) ?: "General" }
            .mapValues { (_, group) -> group.size }
            .toList()
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
            .ifEmpty { listOf("General") }

        val avgSessionMinutes = sessionsLast7
            .takeIf { it.isNotEmpty() }
            ?.let { list -> (list.sumOf { it.durationSec } / list.size / 60).coerceAtLeast(1) }
            ?: 0

        val accuracy = sessionsLast7
            .mapNotNull { it.accuracyPercent }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt()
            ?: 0

        val currentImage = profile?.profileImageUrl ?: _uiState.value.profileImageUri

        return ProfileUiState(
            isLoading = false,
            userId = authUser.id,
            email = profile?.email ?: authUser.email,
            displayName = displayName,
            memberSince = memberSince,
            heightCm = heightCm,
            weightKg = weightKg,
            bmi = bmi,
            focusArea = profile?.fitnessGoal?.takeIf(String::isNotBlank) ?: "General Fitness",
            weeklyGoalSessions = weeklyGoalSessions,
            goalProgressPercent = weeklyProgress,
            targetWeightKg = profile?.targetWeightKg,
            preferredExercises = preferredExercises,
            sessionLengthText = if (avgSessionMinutes > 0) "$avgSessionMinutes min" else "Not enough data",
            difficultyText = when {
                accuracy >= 85 -> "Advanced"
                accuracy >= 70 -> "Intermediate"
                sessions.isEmpty() -> "Getting Started"
                else -> "Beginner"
            },
            healthInfo = if (sessions.isEmpty()) {
                listOf(
                    "No workout history yet",
                    "Complete sessions to unlock personalized insights",
                )
            } else {
                listOf(
                    "Consistency (7d): ${sessionsLast7.size}/7 days",
                    "Avg accuracy (7d): $accuracy%",
                )
            },
            profileImageUri = currentImage,
            questionnaireSummary = onboarding?.let {
                QuestionnaireSummary(
                    goal = it.goal ?: "Not set",
                    experienceLevel = it.experienceLevel ?: "Not set",
                    focusAreas = it.focusAreas?.split(",")?.map { s -> s.trim() }
                        ?.filter { s -> s.isNotEmpty() }?.joinToString(", ") ?: "Not set",
                )
            },
        )
    }

    private fun sessionsLastDays(sessions: List<WorkoutSession>, days: Int): List<WorkoutSession> {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(days - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return sessions.filter { it.startedAtEpochMs >= start }
    }

    private fun memberSinceText(sessions: List<WorkoutSession>): String {
        if (sessions.isEmpty()) return "MEMBER SINCE THIS YEAR"
        val first = sessions.minByOrNull { it.startedAtEpochMs } ?: return "MEMBER SINCE THIS YEAR"
        val cal = Calendar.getInstance().apply { timeInMillis = first.startedAtEpochMs }
        return "MEMBER SINCE ${cal.get(Calendar.YEAR)}"
    }

    private fun calculateBmi(heightCm: Int?, weightKg: Double?): Double? {
        if (heightCm == null || weightKg == null || heightCm <= 0 || weightKg <= 0.0) return null
        val meters = heightCm / 100.0
        return (weightKg / (meters * meters) * 10.0).roundToInt() / 10.0
    }
}

data class QuestionnaireSummary(
    val goal: String,
    val experienceLevel: String,
    val focusAreas: String,
)

data class ProfileUiState(
    val isLoading: Boolean = false,
    val userId: String? = null,
    val email: String? = null,
    val displayName: String = "User",
    val memberSince: String = "MEMBER SINCE THIS YEAR",
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val bmi: Double? = null,
    val focusArea: String = "General Fitness",
    val targetWeightKg: Double? = null,
    val weeklyGoalSessions: Int = 4,
    val goalProgressPercent: Int = 0,
    val preferredExercises: List<String> = emptyList(),
    val sessionLengthText: String = "Not enough data",
    val difficultyText: String = "Getting Started",
    val healthInfo: List<String> = emptyList(),
    val dailyReminders: Boolean = true,
    val goalAchievements: Boolean = true,
    val saveCompleted: Boolean = false,
    val saveErrorMessage: String? = null,
    val signOutCompleted: Boolean = false,
    val profileImageUri: String? = null,
    val questionnaireSummary: QuestionnaireSummary? = null,
)