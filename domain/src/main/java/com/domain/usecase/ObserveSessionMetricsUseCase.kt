package com.domain.usecase

import com.domain.model.SessionMetric
import com.domain.repository.SessionMetricsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSessionMetricsUseCase @Inject constructor(
    private val sessionMetricsRepository: SessionMetricsRepository,
) {
    operator fun invoke(userId: String, limit: Int = 30): Flow<List<SessionMetric>> =
        sessionMetricsRepository.observeSessionMetrics(userId, limit)
}
