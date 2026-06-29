package com.domain.usecase

import com.domain.model.AuthUser
import com.domain.repository.AuthRepository
import javax.inject.Inject

class SignUpWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(name: String, email: String, password: String): Result<AuthUser> =
        authRepository.signUpWithEmail(name, email, password)
}
