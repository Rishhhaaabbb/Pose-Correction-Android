package com.posemonitor2.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domain.usecase.GetAdminStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminStatsViewModel @Inject constructor(
    private val getAdminStatsUseCase: GetAdminStatsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminStatsUiState())
    val uiState: StateFlow<AdminStatsUiState> = _uiState.asStateFlow()

    init {
        loadRealtimeStats()
    }

    private fun loadRealtimeStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val stats = getAdminStatsUseCase()
            _uiState.value = AdminStatsUiState(
                totalUsers = stats.totalUsers,
                newMembers = stats.newMembers,
                liveUsers = stats.liveUsers,
                growthData = stats.growthData,
                isLoading = false
            )
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
