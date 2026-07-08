package com.posemonitor2.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.Exercise
import com.domain.model.OnboardingResponse
import com.domain.model.UserProfile
import com.domain.model.WorkoutSession
import com.domain.repository.AuthRepository
import com.domain.repository.ExerciseRepository
import com.domain.repository.OnboardingRepository
import com.domain.repository.WorkoutSessionRepository
import com.domain.usecase.GetRecommendedExercisesUseCase
import com.domain.usecase.ObserveProfileUseCase
import com.domain.usecase.ObserveWorkoutSessionsUseCase
import com.domain.usecase.RestoreSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val observeProfileUseCase: ObserveProfileUseCase,
    private val observeWorkoutSessionsUseCase: ObserveWorkoutSessionsUseCase,
    private val authRepository: AuthRepository,
    private val exerciseRepository: ExerciseRepository,
    private val onboardingRepository: OnboardingRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val getRecommendedExercisesUseCase: GetRecommendedExercisesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<Exercise>()
    val navigationEvents: SharedFlow<Exercise> = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch { restoreSessionUseCase() }

        // Single reactive stream combining profile + sessions + onboarding answers.
        // This eliminates the race condition that existed with two separate coroutines.
        viewModelScope.launch {
            authRepository.observeSession().collect { authUser ->
                val userId = authUser?.id?.takeIf { it.isNotBlank() }
                    ?: ProgressViewModel.DEFAULT_USER_ID

                combine(
                    observeProfileUseCase(userId),
                    observeWorkoutSessionsUseCase(userId, limit = 100),
                    onboardingRepository.observeAnswers(userId),
                ) { profile, sessions, onboardingAnswers ->
                    Triple(profile, sessions, onboardingAnswers)
                }.collect { (profile, sessions, onboardingAnswers) ->

                    val todayMinutes = calculateTodayMinutes(sessions)
                    // dailyGoalMinutes comes from onboarding answers — resolved here in the
                    // same combine, so there's no race with a separate coroutine.
                    val dailyGoal = onboardingAnswers?.dailyPracticeMinutes ?: 20
                    val greetingSubtitle = buildGreetingSubtitle(onboardingAnswers)
                    val bmi = calculateBmi(profile?.heightCm, profile?.weightKg)
                    val streak = calculateStreak(sessions)

                    val displayName = if (authUser != null) {
                        profile?.displayName?.takeIf(String::isNotBlank)
                            ?: authUser.fullName?.takeIf(String::isNotBlank)
                            ?: authUser.email?.substringBefore('@')?.takeIf(String::isNotBlank)
                            ?: "User"
                    } else {
                        profile?.displayName?.takeIf(String::isNotBlank) ?: "User"
                    }

                    _uiState.value = _uiState.value.copy(
                        displayName = displayName,
                        bmi = bmi,
                        weightKg = profile?.weightKg,
                        targetWeightKg = profile?.targetWeightKg,
                        currentStreak = streak.first,
                        bestStreak = streak.second,
                        userEmail = authUser?.email,
                        todayMinutesDone = todayMinutes,
                        dailyGoalMinutes = dailyGoal,
                        dailyGoalProgress = computeGoalProgress(todayMinutes, dailyGoal),
                        greetingSubtitle = greetingSubtitle,
                    )

                    // Load exercise recommendations when profile + answers are available
                    if (profile != null && onboardingAnswers != null) {
                        loadRecommendations(userId, profile, onboardingAnswers)
                    }
                }
            }
        }
    }

    private suspend fun loadRecommendations(
        userId: String,
        profile: UserProfile,
        answers: OnboardingResponse,
    ) {
        val allExercises = exerciseRepository.getAll()
        val recentIds = workoutSessionRepository.getSessionsInLast24Hours(userId)
            .mapNotNull { it.exerciseName }
            .toSet()

        val recs = getRecommendedExercisesUseCase(
            profile = profile,
            allExercises = allExercises,
            recentExerciseIds = recentIds,
        )

        _uiState.value = _uiState.value.copy(
            recommendedExercises = recs,
        )
    }

    private fun buildGreetingSubtitle(answers: OnboardingResponse?): String {
        return when (answers?.goal?.trim()) {
            "Improve Flexibility" -> "Let's work on your flexibility today"
            "Improve Posture"     -> "Let's improve your posture today"
            "Reduce Stress"       -> "Time for a calming session"
            "Lose Weight"         -> "Let's burn some calories today"
            "Build Strength"      -> "Let's build strength today"
            "Increase Mobility"   -> "Let's increase your mobility today"
            "General Fitness"     -> "Let's keep up the great work today"
            else                  -> "Ready for your session today?"
        }
    }

    fun onExerciseClicked(exercise: Exercise) {
        viewModelScope.launch { _navigationEvents.emit(exercise) }
    }

    private fun calculateBmi(heightCm: Int?, weightKg: Double?): Double? {
        if (heightCm == null || weightKg == null || heightCm <= 0 || weightKg <= 0.0) return null
        val meters = heightCm / 100.0
        return (weightKg / (meters * meters) * 10.0).roundToInt() / 10.0
    }

    private fun calculateStreak(sessions: List<WorkoutSession>): Pair<Int, Int> {
        if (sessions.isEmpty()) return Pair(0, 0)
        val daySet = sessions.map { startOfDayMillis(it.startedAtEpochMs) }.toSet()
        val sortedDays = daySet.toList().sorted()
        var best = 1
        var currentRun = 1
        for (i in 1 until sortedDays.size) {
            if (sortedDays[i] - sortedDays[i - 1] == DAY_MS) {
                currentRun++
                if (currentRun > best) best = currentRun
            } else {
                currentRun = 1
            }
        }
        var current = 0
        val todayStart = startOfDayMillis(System.currentTimeMillis())
        var probe = addDays(todayStart, -1)
        if (daySet.contains(todayStart)) { current = 1; probe = addDays(todayStart, -1) }
        while (daySet.contains(probe)) { current++; probe = addDays(probe, -1) }
        return Pair(current, best)
    }

    private fun startOfDayMillis(epochMs: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun addDays(epochMs: Long, days: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    private fun calculateTodayMinutes(sessions: List<WorkoutSession>): Int {
        val todayStart = startOfDayMillis(System.currentTimeMillis())
        return (sessions.filter { it.startedAtEpochMs >= todayStart }.sumOf { it.durationSec } / 60).coerceAtLeast(0)
    }

    private fun computeGoalProgress(todayMinutes: Int, goalMinutes: Int): Float {
        if (goalMinutes <= 0) return 0f
        return (todayMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}

data class HomeUiState(
    val displayName: String = "User",
    val bmi: Double? = null,
    val weightKg: Double? = null,
    val targetWeightKg: Double? = null,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val userEmail: String? = null,
    val recommendedExercises: List<Exercise> = emptyList(),
    val greetingSubtitle: String = "",
    val dailyGoalMinutes: Int = 20,
    val todayMinutesDone: Int = 0,
    val dailyGoalProgress: Float = 0f,
)
