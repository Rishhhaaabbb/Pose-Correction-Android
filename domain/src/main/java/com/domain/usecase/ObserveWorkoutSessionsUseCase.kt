package com.domain.usecase

import com.domain.model.WorkoutSession
import com.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveWorkoutSessionsUseCase @Inject constructor(
    private val workoutSessionRepository: WorkoutSessionRepository,
) {
    operator fun invoke(userId: String, limit: Int = 100): Flow<List<WorkoutSession>> =
        workoutSessionRepository.observeSessions(userId, limit)
}
