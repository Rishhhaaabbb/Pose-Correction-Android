package com.posemonitor2.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AdminStatsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionDao: com.data.local.dao.SessionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminStatsUiState())
    val uiState: StateFlow<AdminStatsUiState> = _uiState.asStateFlow()

    init {
        loadRealtimeStats()
    }

    private fun loadRealtimeStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 1. Total Users from Firestore
                val profilesSnapshot = firestore.collection("profiles").get().await()
                val totalUsers = profilesSnapshot.size()
                
                // 2. Total Workout Sessions (completed) from Firestore
                val sessionsSnapshot = firestore.collection("workout_sessions")
                    .whereEqualTo("status", "COMPLETED")
                    .get().await()
                val totalSessions = sessionsSnapshot.size()
                
                // 3. Active users in the last 7 days (unique user IDs)
                val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                val weekSnapshot = firestore.collection("workout_sessions")
                    .whereGreaterThan("ended_at", sevenDaysAgo)
                    .get().await()
                val activeUserIds = weekSnapshot.documents.mapNotNull { doc ->
                    doc.getString("user_id")
                }.toSet()
                val activeThisWeek = activeUserIds.size

                // 4. New members this week (profiles created in last 7 days)
                val newMembersSnapshot = firestore.collection("profiles")
                    .whereGreaterThan("created_at", sevenDaysAgo)
                    .get().await()
                val newMembers = newMembersSnapshot.size()

                // 5. Weekly growth data: sessions per day over last 7 days
                val dayMillis = 24L * 60 * 60 * 1000
                val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % dayMillis)
                val growthData = (6 downTo 0).map { daysAgo ->
                    val dayStart = todayStart - (daysAgo * dayMillis)
                    val dayEnd = dayStart + dayMillis
                    val daySessions = weekSnapshot.documents.count { doc ->
                        val endedAt = doc.getLong("ended_at") ?: 0L
                        endedAt in dayStart until dayEnd
                    }
                    // Normalize to 0..1 relative to peak
                    daySessions.toFloat()
                }
                val peak = growthData.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                val normalizedGrowth = growthData.map { it / peak }

                _uiState.value = AdminStatsUiState(
                    totalUsers = totalUsers,
                    newMembers = newMembers,
                    liveUsers = activeThisWeek,
                    growthData = normalizedGrowth,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = AdminStatsUiState(
                    totalUsers = 0,
                    newMembers = 0,
                    liveUsers = 0,
                    growthData = emptyList(),
                    isLoading = false
                )
            }
        }
    }
}

data class AdminStatsUiState(
    val totalUsers: Int = 0,
    val newMembers: Int = 0,
    val liveUsers: Int = 0,
    val growthData: List<Float> = emptyList(),
    val isLoading: Boolean = false
)
