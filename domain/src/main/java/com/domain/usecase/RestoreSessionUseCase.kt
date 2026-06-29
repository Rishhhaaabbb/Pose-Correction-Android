package com.domain.usecase

import com.domain.model.AuthUser
import com.domain.repository.AuthRepository
import javax.inject.Inject

class RestoreSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Result<AuthUser?> = authRepository.restoreSession()
}
