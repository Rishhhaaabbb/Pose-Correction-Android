package com.domain.usecase

import com.domain.model.WorkoutSession
import com.domain.repository.WorkoutSessionRepository
import javax.inject.Inject

class SaveWorkoutSessionUseCase @Inject constructor(
    private val workoutSessionRepository: WorkoutSessionRepository,
) {
    suspend operator fun invoke(session: WorkoutSession): Result<Unit> =
        workoutSessionRepository.saveSession(session)
}
