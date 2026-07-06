package com.domain.usecase

import com.domain.model.DailyStat
import com.domain.repository.DailyStatsRepository
import javax.inject.Inject

class UpsertDailyStatUseCase @Inject constructor(
    private val dailyStatsRepository: DailyStatsRepository,
) {
    suspend operator fun invoke(stat: DailyStat): Result<Unit> =
        dailyStatsRepository.upsertDailyStat(stat)
}
