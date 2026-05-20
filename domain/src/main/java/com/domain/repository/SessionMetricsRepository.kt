package com.domain.repository

import com.domain.model.SessionMetric
import kotlinx.coroutines.flow.Flow

interface SessionMetricsRepository {
    fun observeSessionMetrics(userId: String, limit: Int): Flow<List<SessionMetric>>

    suspend fun saveSessionMetric(metric: SessionMetric): Result<Unit>
}
