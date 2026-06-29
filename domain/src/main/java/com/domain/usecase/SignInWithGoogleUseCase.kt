package com.domain.usecase

import com.domain.model.AuthUser
import com.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(idToken: String): Result<AuthUser> =
        authRepository.signInWithGoogleIdToken(idToken)
}
