package com.domain.usecase

import com.domain.repository.WorkoutSessionRepository
import javax.inject.Inject

class DeleteWorkoutSessionUseCase @Inject constructor(
    private val workoutSessionRepository: WorkoutSessionRepository,
) {
    suspend operator fun invoke(sessionId: String): Result<Unit> =
        workoutSessionRepository.deleteSession(sessionId)
}
