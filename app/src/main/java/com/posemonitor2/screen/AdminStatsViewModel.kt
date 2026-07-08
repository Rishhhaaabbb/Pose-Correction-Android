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
                // Log current auth state
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                android.util.Log.d("AdminStatsViewModel", "=== AUTH STATE ===")
                android.util.Log.d("AdminStatsViewModel", "Current user: ${currentUser?.email}")
                android.util.Log.d("AdminStatsViewModel", "User UID: ${currentUser?.uid}")
                android.util.Log.d("AdminStatsViewModel", "Is anonymous: ${currentUser?.isAnonymous}")
                
                // 1. Total Users from Firestore (NO FALLBACK)
                android.util.Log.d("AdminStatsViewModel", "=== FETCHING FROM FIRESTORE ===")
                val profilesSnapshot = firestore.collection("profiles").get().await()
                val totalUsers = profilesSnapshot.size()
                android.util.Log.d("AdminStatsViewModel", "✓ Firestore profiles count: $totalUsers")
                
                // 2. Total Workout Sessions (completed) from Firestore
                val sessionsSnapshot = firestore.collection("workout_sessions")
                    .whereEqualTo("status", "COMPLETED")
                    .get().await()
                val totalSessions = sessionsSnapshot.size()
                android.util.Log.d("AdminStatsViewModel", "✓ Firestore total sessions: $totalSessions")
                
                // 3. Recent sessions (last 24 hours) from Firestore
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                val recentSnapshot = firestore.collection("workout_sessions")
                    .whereGreaterThan("ended_at", oneDayAgo)
                    .get().await()
                val recentSessions = recentSnapshot.size()
                android.util.Log.d("AdminStatsViewModel", "✓ Firestore recent sessions (24h): $recentSessions")

                // 4. Growth Data
                val growthData = listOf(0.8f, 0.9f, 0.85f, 0.5f, 0.2f, 0.8f, 0.3f)

                android.util.Log.d("AdminStatsViewModel", "=== FINAL STATS ===")
                android.util.Log.d("AdminStatsViewModel", "Users: $totalUsers, Sessions: $totalSessions, Recent: $recentSessions")

                _uiState.value = AdminStatsUiState(
                    totalUsers = totalUsers,
                    newMembers = recentSessions,
                    liveUsers = totalSessions,
                    growthData = growthData,
                    isLoading = false
                )
            } catch (e: Exception) {
                android.util.Log.e("AdminStatsViewModel", "=== FIRESTORE ERROR ===")
                android.util.Log.e("AdminStatsViewModel", "Error type: ${e.javaClass.simpleName}")
                android.util.Log.e("AdminStatsViewModel", "Error message: ${e.message}")
                android.util.Log.e("AdminStatsViewModel", "Full error:", e)
                
                _uiState.value = AdminStatsUiState(
                    totalUsers = 0,
                    newMembers = 0,
                    liveUsers = 0,
                    growthData = listOf(0.8f, 0.9f, 0.85f, 0.5f, 0.2f, 0.8f, 0.3f),
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
