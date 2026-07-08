package com.domain.repository

import com.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface WorkoutSessionRepository {
    fun observeSessions(userId: String, limit: Int): Flow<List<WorkoutSession>>

    suspend fun saveSession(session: WorkoutSession): Result<Unit>

    /** Deletes a session by ID from local storage (and remote if reachable). */
    suspend fun deleteSession(sessionId: String): Result<Unit>

    /** Returns all sessions for [userId] that started within the last 24 hours. */
    suspend fun getSessionsInLast24Hours(userId: String): List<WorkoutSession>
}