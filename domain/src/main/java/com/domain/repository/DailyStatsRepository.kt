package com.domain.repository

import com.domain.model.DailyStat
import kotlinx.coroutines.flow.Flow

interface DailyStatsRepository {
    fun observeDailyStats(userId: String, limit: Int): Flow<List<DailyStat>>

    suspend fun upsertDailyStat(stat: DailyStat): Result<Unit>
}
