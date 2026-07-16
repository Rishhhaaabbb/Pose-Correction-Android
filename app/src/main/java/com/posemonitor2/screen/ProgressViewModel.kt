package com.posemonitor2.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.WorkoutSession
import com.domain.repository.AuthRepository
import com.domain.usecase.ObserveAuthSessionUseCase
import com.domain.usecase.ObserveWorkoutSessionsUseCase
import com.domain.usecase.RestoreSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel @Inject constructor(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    private val observeWorkoutSessionsUseCase: ObserveWorkoutSessionsUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState(isLoading = true))
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()
    private var allSessions: List<WorkoutSession> = emptyList()

    init {
        viewModelScope.launch {
            restoreSessionUseCase()
        }

        viewModelScope.launch {
            authRepository.observeSession()
                .collect { authUser ->
                    val userId = authUser?.id
                    if (userId.isNullOrBlank()) {
                        // Fall back to default user - check for local sessions
                        val defaultUserId = DEFAULT_USER_ID
                        observeWorkoutSessionsUseCase(defaultUserId, limit = 200).collect { sessions ->
                            allSessions = sessions
                            _uiState.value = withContext(Dispatchers.Default) { buildUiState(sessions, _uiState.value.selectedRange) }
                        }
                    } else {
                        observeWorkoutSessionsUseCase(userId, limit = 200).collect { sessions ->
                            allSessions = sessions
                            _uiState.value = withContext(Dispatchers.Default) { buildUiState(sessions, _uiState.value.selectedRange) }
                        }
                    }
                }
        }
    }

    fun selectRange(range: StatsRange) {
        _uiState.update { it.copy(selectedRange = range) }
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = buildUiState(allSessions, range)
        }
    }

    private fun buildUiState(sessions: List<WorkoutSession>, selectedRange: StatsRange): ProgressUiState {
        if (sessions.isEmpty()) {
            return ProgressUiState(isLoading = false, selectedRange = selectedRange)
        }

        val now = Calendar.getInstance()
        val todayStart = startOfDayMillis(now.timeInMillis)
        val rangeStart = addDays(todayStart, -(selectedRange.days - 1))
        val last7Start = addDays(todayStart, -6)
        val prevRangeStart = addDays(rangeStart, -selectedRange.days)
        val prevRangeEnd = addDays(rangeStart, -1)

        val sessionsInRange = sessions.filter { it.startedAtEpochMs in rangeStart..(todayStart + DAY_MS - 1) }
        val sessionsLast7 = sessions.filter { it.startedAtEpochMs in last7Start..(todayStart + DAY_MS - 1) }
        val sessionsPrevRange = sessions.filter { it.startedAtEpochMs in prevRangeStart..(prevRangeEnd + DAY_MS - 1) }

        val totalMinutes = (sessionsInRange.sumOf { it.durationSec } / 60).coerceAtLeast(0)
        val avgMinutes = if (sessionsInRange.isEmpty()) 0 else (totalMinutes / sessionsInRange.size)
        val consistencyPercent = ((distinctDayKeys(sessionsInRange).size / selectedRange.days.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        val totalReps = sessionsInRange.sumOf { it.totalReps ?: 0 }
        val avgAccuracy = sessionsInRange.mapNotNull { it.accuracyPercent }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0

        val weeklyBuckets = (0..6).map { i ->
            val dayStart = addDays(todayStart, -6 + i)
            DayBucket(dayStart = dayStart, label = weekdayLabel(dayStart), count = 0)
        }.toMutableList()

        sessionsLast7.forEach { session ->
            val key = startOfDayMillis(session.startedAtEpochMs)
            val index = weeklyBuckets.indexOfFirst { it.dayStart == key }
            if (index >= 0) {
                weeklyBuckets[index] = weeklyBuckets[index].copy(count = weeklyBuckets[index].count + 1)
            }
        }

        val maxCount = weeklyBuckets.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        val weeklyActivity = weeklyBuckets.map { bucket ->
            bucket.label to (bucket.count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
        }

        val distribution = sessionsInRange
            .groupBy { it.exerciseName?.takeIf(String::isNotBlank) ?: "General" }
            .mapValues { (_, group) -> group.size }
            .toList()
            .sortedByDescending { it.second }

        val totalForDistribution = distribution.sumOf { it.second }.coerceAtLeast(1)
        val distributionItems = distribution.take(4).mapIndexed { index, (label, count) ->
            DistributionUiItem(
                label = label,
                percent = ((count.toDouble() / totalForDistribution.toDouble()) * 100.0).roundToInt().coerceIn(1, 100),
                colorIndex = index,
            )
        }

        val recentSessions = sessions
            .sortedByDescending { it.startedAtEpochMs }
            .take(5)
            .map { session ->
                val minutes = (session.durationSec / 60).coerceAtLeast(1)
                val isPartial = session.status != com.domain.model.SessionStatus.COMPLETED
                val completionPercent = if (isPartial && (session.targetReps ?: 0) > 0) {
                    ((session.totalReps ?: 0) * 100 / session.targetReps!!).coerceIn(0, 99)
                } else 100
                RecentSessionUiItem(
                    title = session.exerciseName?.takeIf(String::isNotBlank),
                    startedAtEpochMs = session.startedAtEpochMs,
                    durationMinutes = minutes,
                    isPartial = isPartial,
                    completionPercent = completionPercent,
                )
            }

        val streakInfo = computeStreak(sessions)
        val activeDays = distinctDayKeys(sessions)
        val allSessionsUi = sessions.sortedByDescending { it.startedAtEpochMs }.take(200).map { s ->
            val minutes = (s.durationSec / 60).coerceAtLeast(1)
            val isPartial = s.status != com.domain.model.SessionStatus.COMPLETED
            val completionPercent = if ((s.totalReps ?: 0) > 0 && (s.targetReps ?: 0) > 0) {
                (((s.totalReps ?: 0).toFloat() / (s.targetReps ?: 1)) * 100).toInt().coerceIn(0, 100)
            } else 100
            RecentSessionUiItem(
                title = s.exerciseName,
                startedAtEpochMs = s.startedAtEpochMs,
                durationMinutes = minutes,
                isPartial = isPartial,
                completionPercent = completionPercent,
            )
        }

        return ProgressUiState(
            isLoading = false,
            selectedRange = selectedRange,
            sessionsCount = sessionsInRange.size,
            sessionsDeltaVsLastWeek = sessionsInRange.size - sessionsPrevRange.size,
            totalTimeMinutes = totalMinutes,
            avgDurationMinutes = avgMinutes,
            consistencyPercent = consistencyPercent,
            totalReps = totalReps,
            avgAccuracyPercent = avgAccuracy,
            weeklyActivity = weeklyActivity,
            distribution = distributionItems,
            dailyStreakDays = streakInfo.current,
            bestStreakDays = streakInfo.best,
            recentSessions = recentSessions,
            allSessions = allSessionsUi,
            activeDays = activeDays,
        )
    }

    private fun distinctDayKeys(sessions: List<WorkoutSession>): Set<Long> =
        sessions.map { startOfDayMillis(it.startedAtEpochMs) }.toSet()

    private fun computeStreak(sessions: List<WorkoutSession>): StreakInfo {
        if (sessions.isEmpty()) return StreakInfo(0, 0)

        val daySet = sessions.map { startOfDayMillis(it.startedAtEpochMs) }.toSet()
        
        // Find the most recent session date
        val mostRecentSessionDay = daySet.maxOrNull() ?: return StreakInfo(0, 0)
        
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

        // Calculate current streak - check from yesterday, not today
        // (user might not have worked out yet today)
        var current = 0
        var probe = addDays(startOfDayMillis(System.currentTimeMillis()), -1)
        
        // First check if today has a session (in case user already worked out today)
        val todayStart = startOfDayMillis(System.currentTimeMillis())
        if (daySet.contains(todayStart)) {
            current = 1
            probe = addDays(todayStart, -1)
        }
        
        // Count consecutive days backwards from the most recent day
        while (daySet.contains(probe)) {
            current += 1
            probe = addDays(probe, -1)
        }

        return StreakInfo(current = current, best = best)
    }

    private fun weekdayLabel(epochMs: Long): String =
        java.text.SimpleDateFormat("EE", Locale.getDefault()).format(java.util.Date(epochMs)).first().toString()

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

    private data class StreakInfo(val current: Int, val best: Int)
    private data class DayBucket(val dayStart: Long, val label: String, val count: Int)

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        const val DEFAULT_USER_ID = "default-local-user"
    }
}

data class ProgressUiState(
    val isLoading: Boolean = false,
    val selectedRange: StatsRange = StatsRange.DAYS_7,
    val sessionsCount: Int = 0,
    val sessionsDeltaVsLastWeek: Int = 0,
    val totalTimeMinutes: Int = 0,
    val avgDurationMinutes: Int = 0,
    val consistencyPercent: Int = 0,
    val totalReps: Int = 0,
    val avgAccuracyPercent: Int = 0,
    val weeklyActivity: List<Pair<String, Float>> = emptyList(),
    val distribution: List<DistributionUiItem> = emptyList(),
    val dailyStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val recentSessions: List<RecentSessionUiItem> = emptyList(),
    val allSessions: List<RecentSessionUiItem> = emptyList(),
    val activeDays: Set<Long> = emptySet(),
)

enum class StatsRange(val days: Int, val label: String) {
    DAYS_7(7, "7D"),
    DAYS_30(30, "30D"),
    DAYS_90(90, "90D"),
}

data class DistributionUiItem(
    val label: String,
    val percent: Int,
    val colorIndex: Int,
)

data class RecentSessionUiItem(
    val title: String?,
    val startedAtEpochMs: Long,
    val durationMinutes: Int,
    val isPartial: Boolean = false,
    val completionPercent: Int = 100,
)