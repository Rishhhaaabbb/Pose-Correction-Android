package com.domain.usecase

import com.domain.model.AdminStats
import com.domain.repository.AdminStatsRepository
import javax.inject.Inject

class GetAdminStatsUseCase @Inject constructor(
    private val adminStatsRepository: AdminStatsRepository,
) {
    suspend operator fun invoke(): AdminStats = adminStatsRepository.fetchStats()
}
