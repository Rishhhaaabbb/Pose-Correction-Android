package com.domain.usecase

import com.domain.model.AuthUser
import com.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthUser> =
        authRepository.signInWithEmail(email, password)
}
