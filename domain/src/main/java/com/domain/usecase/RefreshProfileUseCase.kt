package com.domain.usecase

import com.domain.repository.ProfileRepository
import javax.inject.Inject

class RefreshProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(userId: String): Result<Unit> = profileRepository.refreshProfile(userId)
}
