package com.domain.repository

import com.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface WorkoutSessionRepository {
    fun observeSessions(userId: String, limit: Int): Flow<List<WorkoutSession>>

    suspend fun saveSession(session: WorkoutSession): Result<Unit>
}
