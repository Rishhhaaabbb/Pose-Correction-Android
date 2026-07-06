package com.domain.usecase

import com.domain.model.DailyStat
import com.domain.repository.DailyStatsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDailyStatsUseCase @Inject constructor(
    private val dailyStatsRepository: DailyStatsRepository,
) {
    operator fun invoke(userId: String, limit: Int = 30): Flow<List<DailyStat>> =
        dailyStatsRepository.observeDailyStats(userId, limit)
}
