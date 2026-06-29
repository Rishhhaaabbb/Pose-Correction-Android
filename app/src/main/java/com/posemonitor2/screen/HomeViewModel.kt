package com.posemonitor2.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.AuthUser
import com.domain.model.UserProfile
import com.domain.model.WorkoutSession
import com.domain.repository.AuthRepository
import com.domain.usecase.ObserveAuthSessionUseCase
import com.domain.usecase.ObserveProfileUseCase
import com.domain.usecase.ObserveWorkoutSessionsUseCase
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            restoreSessionUseCase()
        }

        viewModelScope.launch {
            authRepository.observeSession()
                .collect { authUser ->
                    val userId = authUser?.id
                    if (userId.isNullOrBlank()) {
                        // Fall back to default user
                        val defaultUserId = ProgressViewModel.DEFAULT_USER_ID
                        combine(
                            observeProfileUseCase(defaultUserId),
                            observeWorkoutSessionsUseCase(defaultUserId, limit = 100),
                        ) { profile, sessions ->
                            buildHomeUiStateForDefaultUser(profile, sessions)
                        }.collect { newState ->
                            _uiState.value = newState
                        }
                    } else {
                        combine(
                            observeProfileUseCase(userId),
                            observeWorkoutSessionsUseCase(userId, limit = 100),
                            flowOf(authUser)
                        ) { profile, sessions, auth ->
                            buildHomeUiState(profile, sessions, auth!!)
                        }.collect { newState ->
                            _uiState.value = newState
                        }
                    }
                }
        }
    }

    private fun buildHomeUiStateForDefaultUser(profile: UserProfile?, sessions: List<WorkoutSession>): HomeUiState {
        val displayName = profile?.displayName?.takeIf(String::isNotBlank) ?: "User"

        val heightCm = profile?.heightCm
        val weightKg = profile?.weightKg
        val targetWeight = profile?.targetWeightKg

        val bmi = calculateBmi(heightCm, weightKg)

        val streak = calculateStreak(sessions)

        return HomeUiState(
            displayName = displayName,
            bmi = bmi,
            weightKg = weightKg,
            targetWeightKg = targetWeight,
            currentStreak = streak.first,
            bestStreak = streak.second,
        )
    }

    private fun buildHomeUiState(profile: UserProfile?, sessions: List<WorkoutSession>, authUser: AuthUser): HomeUiState {
        val displayName = profile?.displayName?.takeIf(String::isNotBlank)
            ?: authUser.fullName?.takeIf(String::isNotBlank)
            ?: authUser.email?.substringBefore('@')?.takeIf(String::isNotBlank)
            ?: "User"

        val heightCm = profile?.heightCm
        val weightKg = profile?.weightKg
        val targetWeight = profile?.targetWeightKg

        val bmi = calculateBmi(heightCm, weightKg)

        val streak = calculateStreak(sessions)

        return HomeUiState(
            displayName = displayName,
            bmi = bmi,
            weightKg = weightKg,
            targetWeightKg = targetWeight,
            currentStreak = streak.first,
            bestStreak = streak.second,
            userEmail = authUser.email
        )
    }

    private fun calculateBmi(heightCm: Int?, weightKg: Double?): Double? {
        if (heightCm == null || weightKg == null || heightCm <= 0 || weightKg <= 0.0) return null
        val meters = heightCm / 100.0
        return (weightKg / (meters * meters) * 10.0).roundToInt() / 10.0
    }

    private fun calculateStreak(sessions: List<WorkoutSession>): Pair<Int, Int> {
        if (sessions.isEmpty()) return Pair(0, 0)

        val daySet = sessions.map { startOfDayMillis(it.startedAtEpochMs) }.toSet()
        
        // Find the most recent session date
        val mostRecentSessionDay = daySet.maxOrNull() ?: return Pair(0, 0)
        
        // Calculate best streak (consecutive days in history)
        val sortedDays = daySet.toList().sorted()
        var best = 1
        var currentRun = 1
        for (i in 1 until sortedDays.size) {
            if (sortedDays[i] - sortedDays[i - 1] == DAY_MS) {
                currentRun += 1
                if (currentRun > best) best = currentRun
            } else {
                currentRun = 1
            }
        }

        // Calculate current streak - check from yesterday first (user might not have worked out today yet)
        var current = 0
        var probe = addDays(startOfDayMillis(System.currentTimeMillis()), -1)
        
        // First check if today has a session
        val todayStart = startOfDayMillis(System.currentTimeMillis())
        if (daySet.contains(todayStart)) {
            current = 1
            probe = addDays(todayStart, -1)
        }
        
        // Count consecutive days backwards
        while (daySet.contains(probe)) {
            current += 1
            probe = addDays(probe, -1)
        }

        return Pair(current, best)
    }

    private fun startOfDayMillis(epochMs: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun addDays(epochMs: Long, days: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
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
)