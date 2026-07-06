package com.domain.repository

import com.domain.model.OnboardingResponse
import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    suspend fun saveAnswers(answers: OnboardingResponse): Result<Unit>
    suspend fun getAnswers(userId: String): OnboardingResponse?
    fun observeAnswers(userId: String): Flow<OnboardingResponse?>
    suspend fun isCompleted(userId: String): Boolean
}