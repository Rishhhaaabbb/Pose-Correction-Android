package com.domain.repository

import com.domain.model.AdminStats

interface AdminStatsRepository {
    suspend fun fetchStats(): AdminStats
}
