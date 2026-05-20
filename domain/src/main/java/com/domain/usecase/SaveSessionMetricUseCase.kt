package com.domain.usecase

import com.domain.model.SessionMetric
import com.domain.repository.SessionMetricsRepository
import javax.inject.Inject

class SaveSessionMetricUseCase @Inject constructor(
    private val sessionMetricsRepository: SessionMetricsRepository,
) {
    suspend operator fun invoke(metric: SessionMetric): Result<Unit> =
        sessionMetricsRepository.saveSessionMetric(metric)
}
